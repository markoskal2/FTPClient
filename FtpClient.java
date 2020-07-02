//package ce325.hw2;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

public class FtpClient {  
	Socket controlSocket, newConnSocket;
	BufferedReader reader;
	PrintWriter out;
	BufferedReader in;  
	File workingDir;
	String workDir;
	boolean passive, overwrite;
	boolean binary = false;
	
	static boolean DEBUG = false;
	
	enum DBG {IN, OUT};
	
	void dbg(DBG direction, String msg) {
		if(DEBUG) {
			if(direction == DBG.IN)
				System.err.println("<- "+msg); 
			else if(direction == DBG.OUT)
				System.err.println("-> "+msg); 
			else
				System.err.println(msg);
		}
	}
	
	public FtpClient(boolean pasv, boolean overwrite) {
		passive = pasv;
		this.overwrite = overwrite;
		reader = new BufferedReader( new InputStreamReader(System.in) );
		workingDir = new File(".");
		workDir = ".";
	}
	
	public void bindUI(String [] args) {
		String inetAddress;
		int port=0;
		
		try {  
			
			if( args!=null && args.length > 0 ) {
				inetAddress = args[0];
			}
			else {
				System.out.print("Hostname: ");
				inetAddress = reader.readLine();
			}
			
			if( args!=null && args.length > 1 ) {
				port = new Integer( args[1] ).intValue();;
			}
			else {
				System.out.print("Port: ");
				port = new Integer( reader.readLine() ).intValue();
			}
			if( bind(inetAddress, port) ) {
				System.out.println("Socket bind OK!");
			}
			else
				System.out.println("Socket bind FAILED!");
		} catch( IOException ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}
	
	public synchronized boolean bind(String inetAddress, int port) {
		String response = null;
		
		try {
			controlSocket = new Socket(inetAddress, port);
			out = new PrintWriter(controlSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		}
		catch(UnknownHostException ex) {
			System.err.println("Socket host error " + ex.getMessage());
		}
		catch(EOFException ex) {
			System.err.println("End of file error " + ex.getMessage());
		}
		catch(IOException ex) {
			System.err.println("I/O error " + ex.getMessage());
		}
		
		response = readLine();
		System.out.println(response);
		return (response.startsWith("220 "));
		
	}
	
	public synchronized void loginUI() {    
		String username, passwd;
		String socketInput;
		
		try {
			System.out.print("Login Username: ");
			username = reader.readLine();
			System.out.print("Login Password: ");
			passwd = reader.readLine();
			
			if( login(username, passwd) ) 
				System.out.println("Login for user \""+username+"\" OK!");
			else 
				System.out.println("Login for user \""+username+"\" Failed!");
			
		} catch(IOException ex) {
			ex.printStackTrace();
		}    
	}
	
	public synchronized boolean login(String username, String passwd) {
		String response = null;
		
		out.println("USER " + username);
		
		//Pare apantisi apo ton server gia to username
		//An i apantisi pou tha erthei den einai 331 exei ginei kapoio lathos
		response = readLine();
		
		if( !response.startsWith("331") ) {
			System.err.println("User does not exist");
			return false;
		}

		out.println("PASS " + passwd);
		
		//Pare apantisi apo ton server gia to password
		//An ksekinaei me 530 exoume fail sto login
		response = readLine();
		
		if( response.startsWith("530") ) {
			System.err.println("Login incorrect");
			return false;
		}
		
		return true; //Epityxia syndesis ston server
		
	}
	
	//Binary Mode gia tin apostoli arxeiwn
	public synchronized boolean binary() {
		binary = true;
		String response = null;
		
		out.println("TYPE I");
		
		response = readLine();
		
		return ( response.startsWith("200") );
	}
	
	//Ascii mode gia tin apostoli arxeiwn
	public synchronized boolean ascii() {
		binary = false;
		String response = null;
		
		out.println("TYPE A");
		
		response = readLine();
		
		return ( response.startsWith("200") );
	}
	
	public void listUI() {
		try {
			System.out.print("Enter path to list (or . for the current directory): ");
			String path = reader.readLine();
			String info = list(path);      
			List<RemoteFileInfo> list = parse(info);
			for(RemoteFileInfo listinfo : list)
				System.out.println(listinfo);
		} catch(IOException ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}
	
	public String list(String path) {
		String response = null;
		String listing = null;
		
		out.println("PASV");
		
		response = readLine();
		
		if( !response.startsWith("227") ) {
			System.err.println("FTP could not enter passive mode");
			return null;
		}
		
		String ip = null;
		int begin = response.indexOf("(");
		int end = response.indexOf(")", begin + 1);
		
		String sub = response.substring(begin + 1, end);
		String[] tokens = sub.split(",");
		
		//Pare tin IP apo to substring tis morfis (IP1,IP2,IP3,IP4,PORT-MSB,PORT-LSB)
		ip = tokens[0];
		int i;
		for(i = 1; i < tokens.length - 2; i++)
			ip += "." + tokens[i];
		
		//Pare ton arithmo tou port apo ta (PORT-MSB, PORT-LSB)
		String portNumber = Integer.toHexString(Integer.parseInt(tokens[i])) + Integer.toHexString(Integer.parseInt(tokens[i+1]));
		int port = Integer.parseInt(portNumber, 16);
		
		if(!ascii()) {
			System.out.println("Could not enter ASCII mode for listing.");
			return null;
		}
		
		out.println("LIST " + path);
		
		PassiveThreads pasvCon = new PassiveThreads(ip, port, "LIST");
		(new Thread(new PassiveThreads(ip, port, "LIST"))).start();
		
		return listing;
	}
	
	//Ypoklasi gia ta nimata pou dimiourgei o client
	//gia to download/upload arxeiwn kai to directory listing
	class PassiveThreads implements Runnable {
		BufferedInputStream dataInput;
		BufferedReader serverIn;
		File f = null;
		Socket newConnSocket;
		String ip, mode, listing = null;
		StringBuffer dataBuffer = new StringBuffer();
		int port;
		boolean done = false;
		
		public PassiveThreads(String ip, int port, String mode) {
			this.ip = ip;
			this.port = port;
			this.mode = mode;
		}
		
		public PassiveThreads(String ip, int port, String mode, File file) {
			this(ip, port, mode);
			f = file;
		}
		
		public synchronized boolean isDone() {
			if(done) 
				notifyAll();
			
			return done;
		}
		
		public synchronized String getData() {
			return dataBuffer.toString();
		}
		
		public synchronized void downloadFile() {
			try {
				DataInputStream clientData = new DataInputStream(newConnSocket.getInputStream());
				String fileName = clientData.readUTF(); //Pare to onoma tou arxeiou
				long size = clientData.readLong(); //Pare to megethos tou arxeiou
				byte[] retrFile = new byte[1024];
				int readBytes = 0;
				
				while (size > 0 && (readBytes = clientData.read(retrFile, 0, (int) Math.min(retrFile.length, size))) != -1) {
					dataBuffer.append((char) readBytes);
					size -= readBytes;
				}
				
				//Close stream and socket
				clientData.close();
				if(!newConnSocket.isClosed())
					newConnSocket.close();
				
			}
			catch(IOException ex) {
				System.err.println("Cannot download requested file " + ex.getMessage());
				return;
			}
		}
		
		public synchronized void uploadFile(File f) {
			try {
				/*
				BufferedOutputStream oStream = new BufferedOutputStream(newConnSocket.getOutputStream());
				BufferedInputStream iStream = new BufferedInputStream(new FileInputStream(f));
				byte[] storFile = new byte[1024];
				int readBytes = 0;
				while((readBytes = iStream.read(storFile)) != -1) {
					oStream.write(storFile, 0, readBytes);
				}
				oStream.flush();
				oStream.close();
				iStream.close();
				*/
				byte[] storFile = new byte[(int) f.length()];
				dataInput = new BufferedInputStream(new FileInputStream(f));
				
				DataInputStream dataStream = new DataInputStream(dataInput);
				dataStream.readFully(storFile, 0, storFile.length);
				
				//Steile to onoma tou arxeiou kai to megethos tou ston server
				DataOutputStream oStream = new DataOutputStream(newConnSocket.getOutputStream());
				oStream.writeUTF(f.getName());
				oStream.writeLong(storFile.length);
				oStream.write(storFile, 0, storFile.length);
				oStream.flush();
				
				//Close stream and socket
				oStream.close();
				if(!newConnSocket.isClosed())
					newConnSocket.close();
				
			}
			catch(IOException ex) {
				System.err.println("Cannot upload requested file " + ex.getMessage());
				return;
			}
		}
		
		public synchronized void dirList() {
			try {
				serverIn = new BufferedReader(new InputStreamReader(newConnSocket.getInputStream()));
				while((listing = serverIn.readLine()) != null) {
					listing = listing + "###"; //Gia na mporesoume na to parsaroume argotera
				}
				
				serverIn.close();
				if(!newConnSocket.isClosed())
					newConnSocket.close();
			}
			catch(IOException ex) {
				ex.printStackTrace();
			}
		}
		
		public void run() {
			try {
				newConnSocket = new Socket(ip, port);
			}
			catch(IOException ex) {
				System.err.println("Cannot connect to specified host " + ex.getMessage());
			}
			
			if(mode.equals("RETR")) {
				//Theloume na katevasoume to arxeio
				downloadFile();
			}
			else if(mode.equals("STOR")) {
				//Theloume na anevasoume ena arxeio
				uploadFile(f);
			}
			else if(mode.equals("LIST")) {
				//Theloume na kanoume listing
				dirList();
			}
			done = true; //Exei oloklirwthei to download/upload/directory listing
		}
	}
	
	class RemoteFileInfo {
		boolean dir = false; // is directory
		boolean ur = false;  // user read permission
		boolean uw = false;  // user write permission
		boolean ux = false;  // user execute permission
		boolean gr = false;  // group read permission
		boolean gw = false;  // group write permission
		boolean gx = false;  // group execute permission
		boolean or = false;  // other read permission
		boolean ow = false;  // other write permission
		boolean ox = false;  // other execute permission
		long size;           // file size
		String name, perms, line;
		String parentDir;
		String downloadData;
		
		public RemoteFileInfo(String line) {
			this.line = line;
			this.downloadData = line;
		}
		
		private void permissions(String perms) {
		}
		
		public String toString() {
			return this.perms + " " + this.size + " " + this.name;
		}
	}
	
	public synchronized List<RemoteFileInfo> parse(String info) {
		List<RemoteFileInfo> list = new ArrayList<>();
		RemoteFileInfo entry = new RemoteFileInfo(info);
		
		/*H epistrofi apo tin LIST epistrefei 3 dieseis (###) anamesa stin antistoixi
		 * pliroforia, ara kanoume split sto string gia na mporesoume na paroume
		 * ta dedomena poy theloume 
		 */
		String pattern = "###";
		Pattern p = Pattern.compile(pattern);
		String[] tokens = p.split(info); //H epistrofi apo tin ls mporei na periexei perissotera apo ena kena
		String[] subToken = null;
		
		/*Se auti tin epanalipsi pairnoume ena string typou
		 * -rw-rw-r-- 1 markos markos   96 May 23 20:51 hello.c
		 * kai to pairname se ena pinaka subTokens pou kathe thesi tou
		 * exei ena apo ta pedia tou string xwris ta kena
		 * gia na mporesoume na eksagoume ta permissions, to size kai to name.
		 * Kanoume continue giati sinithws to 1o pragma pou epistrefei i ls einai
		 * o arithmos ton arxeiwn ston katalogo (px. total 21)
		 */
		for(int i = 0; i < tokens.length; i++) {
			subToken = tokens[i].split("\\s+");
			
			if(subToken[0].equals("total")) {
				continue;
			}
			
			entry.perms = subToken[0];
			if(entry.perms.charAt(0) == 'd') {
				entry.dir = true;
			}
			entry.size = Long.parseLong(subToken[4]);
			entry.name = subToken[8];
			
			list.add(entry);
		}
		
		return list;
	}
	
	public void uploadUI() {
		try {
			System.out.print("Enter file to upload: ");
			String filepath = reader.readLine();
			File file = new File(filepath);
			mupload(file);
		} catch(IOException ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}
	
	/** 
	 * Upload multiple files
	 * @param f can be either a local filename or local directory
	 */
	public synchronized void mupload(File f) {
		String response = null;
		String filename = f.getName();
		
		out.println("PASV");
		
		response = readLine();
		
		if( !response.startsWith("227") ) {
			System.err.println("FTP could not enter passive mode");
			return;
		}
		
		String ip = null;
		int begin = response.indexOf("(");
		int end = response.indexOf(")", begin + 1);
		
		String sub = response.substring(begin + 1, end);
		String[] tokens = sub.split(",");
		
		//Pare tin IP apo to substring tis morfis (IP1,IP2,IP3,IP4,PORT-MSB,PORT-LSB)
		ip = tokens[0];
		int i;
		for(i = 1; i < tokens.length - 2; i++)
			ip += "." + tokens[i];
		
		//Pare ton arithmo tou port apo ta (PORT-MSB, PORT-LSB)
		String portNumber = Integer.toHexString(Integer.parseInt(tokens[i])) + Integer.toHexString(Integer.parseInt(tokens[i+1]));
		int port = Integer.parseInt(portNumber, 16);
		
		if( !binary() ) {
			System.err.println("FTP Client could not enter Binary mode");
			return;
		}
		
		File[] subFiles = f.listFiles();
		if(subFiles != null && subFiles.length > 0) {
			for(File item : subFiles) {
				if(item.isFile()) {
					
					out.println("STOR " + filename);
					
					response = readLine();
					if( !response.startsWith("150") ) {
						System.err.println("FTP Client could not send file");
						return;
					}
					
					PassiveThreads pasvCon = new PassiveThreads(ip, port, "STOR", f);
					(new Thread(pasvCon)).start();
					while(!pasvCon.isDone()) {
						try{
							this.wait();
						}
						catch(InterruptedException ex) {
							System.err.println("Interrupted while uploading " + ex.getMessage());
							return;
						}
					}
					
				}
				else if(item.isDirectory()) { //Prospathoume na anevasoume directory
					if(!mkdir(filename)) { //Ftiakse to fakelo ston server
						System.err.println("Cannot create directory in server.");
						return;
					}
					
					if(!cwd(filename)) { //Mpes sto directory pou eftiakses
						System.err.println("Cannot enter directory in server.");
						return;
					}
					
					File upFile = new File(item.getName());
					mupload(upFile);
				}
			}
		}
		
		
		response = readLine();
		if( !response.startsWith("226") ) {
			System.err.println("Cannot complete transfer to FTP Server");
			return;
		}
	}
	
	//Apantisi apo ton server
	private String readLine() {
		String response = null;
		try {
			response = in.readLine();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		return response;
	}
	
	public void downloadUI() {
		try {
			System.out.print("Enter file to download: ");
			String filename = reader.readLine();
			File file = new File(filename);                // we have an absolute path
			RemoteFileInfo entry = new RemoteFileInfo(filename);
			
			if( file.exists() && !file.isDirectory()) {
				System.out.println("File \""+file.getPath()+"\" already exist.");
				String yesno;
				do {
					System.out.print("Overwrite (y/n)? ");
					yesno = reader.readLine().toLowerCase();
				} while( !yesno.startsWith("y") && !yesno.startsWith("n") );
				if( yesno.startsWith("n") )
					return;
				else {
					if(mdownload(entry)) {
						FileOutputStream output = new FileOutputStream(file, false);
						output.write((entry.downloadData).getBytes()); //overwrite data
					}
				}
			}
			
		} catch(IOException ex) {
			ex.printStackTrace();
			System.exit(-1);
		}
	}
	
	/** 
	 * Download multiple files
	 * @param entry can be either a filename or directory
	 */
	public synchronized boolean mdownload(RemoteFileInfo entry) {
		String response = null, data = null;
		List<RemoteFileInfo> lst;
		File folder;
		
		out.println("PASV");
		
		response = readLine();
		
		if( !response.startsWith("227") ) {
			System.err.println("FTP could not enter passive mode");
			return false;
		}
		
		String ip = null;
		int begin = response.indexOf("(");
		int end = response.indexOf(")", begin + 1);
		
		String sub = response.substring(begin + 1, end);
		String[] tokens = sub.split(",");
		
		//Pare tin IP apo to substring tis morfis (IP1,IP2,IP3,IP4,PORT-MSB,PORT-LSB)
		ip = tokens[0];
		int i;
		for(i = 1; i < tokens.length - 2; i++)
			ip += "." + tokens[i];
		
		//Pare ton arithmo tou port apo ta (PORT-MSB, PORT-LSB)
		String portNumber = Integer.toHexString(Integer.parseInt(tokens[i])) + Integer.toHexString(Integer.parseInt(tokens[i+1]));
		int port = Integer.parseInt(portNumber, 16);
		
		if( !binary() ) {
			System.err.println("FTP Client could not enter Binary mode");
			return false;
		}
		
		//An einai fakelos pare ta periexomena tou fakelou
		if(entry.dir = true) {
			String info = list(entry.name);
			lst = parse(info);
			
			workDir += "/" + entry.name; //Allakse kai to directory pou douleuoume
			folder = new File(workDir); //Ftiakse ton fakelo kai ston topiko ypologisti
			if(!folder.exists()) {
				if(!folder.mkdir()) {
					System.err.println("Cannot create directory");
					return false;
				}
			}
			Iterator<RemoteFileInfo> itr = lst.iterator();
			while(itr.hasNext()) {
				entry = itr.next();
				mdownload(entry);
			}
			
		}
		else if(entry.dir == false) { //Den einai katalogos alla aplo arxeio
			out.println("RETR " + entry.name);
			
			response = readLine();
			if( !response.startsWith("150") ) {
				System.err.println("Invalid response from RETR command.");
				return false;
			}
			
			PassiveThreads pasvCon = new PassiveThreads(ip, port, "RETR");
			
			if(download(entry, pasvCon) == 0) {
				return true;
			}
			else if(download(entry, pasvCon) == -1) {
				System.out.println("File exists and cannot overwritten");
				return false;
			}
			else if(download(entry, pasvCon) == -2) {
				System.out.println("Download failure");
				return false;
			}
		}
		
		response = readLine();
		return ( response.startsWith("226") );
		
	}
	
	/**
	 * Return values: 
	 *  0: success
	 * -1: File exists and cannot overwritten
	 * -2: download failure
	 */
	public synchronized int download(RemoteFileInfo entry, PassiveThreads pasvCon) {
		
		(new Thread(pasvCon)).start();
		while(!pasvCon.isDone()) {
			try {
				this.wait();
			}
			catch(InterruptedException ex) {
				System.err.println("Interrupted while downloading " + ex.getMessage());
				return -2;
			}
		}
		entry.downloadData = pasvCon.getData();
		return 1;
	}
	
	public synchronized boolean mkdir(String dirname) {
		String response = null;
		
		out.println("MKD " + dirname);
		
		response = readLine();
		
		return (response.startsWith("257"));
	}
	
	public void mkdirUI() {
		String dirname, socketInput;
		try {
			System.out.print("Enter directory name: ");
			dirname = reader.readLine();
			
			if( mkdir(dirname) )
				System.out.println("Directory \""+ dirname +"\" created!" );
			else
				System.out.println("Directory creation failed!");
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public synchronized boolean rmdir(String dirname) {
		String response = null;
		
		out.println("RMD "+ dirname);
		
		response = readLine();
		
		return (response.startsWith("250"));
	}
	
	public void rmdirUI() {
		String dirname, socketInput;
		try {
			System.out.print("Enter directory name: ");
			dirname = reader.readLine();
			
			if( rmdir(dirname) )
				System.out.println("Directory \""+ dirname +"\" deleted!" );
			else
				System.out.println("Directory deletion failed!");
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public synchronized boolean cwd(String dirname) {
		String response = null;
		
		out.println("CWD " + dirname);
		
		response = readLine();
		
		return (response.startsWith("250"));
	}
	
	public void deleteUI() {
		String filename, socketInput;
		try {
			System.out.print("Enter file to delete: ");
			filename = reader.readLine();
			File file = new File(filename);
			
			List<RemoteFileInfo> list = parse( list(filename) );
			if( list.size() > 1 || list.size()==0 || !list.get(0).name.equals(filename) ) {
				File filepath = file.getParentFile() != null ? file.getParentFile() : new File(".");
				list = parse( list( filepath.getPath() ) );
				boolean found = false, deleted = false;
				for(RemoteFileInfo entry : list) 
					if( entry.name.equals(filename) ) {
						found = true;
						if( mdelete( entry ) ) {
							deleted = true;
						}
					}
					if(found && deleted)
						System.out.println("Filename \""+filename+"\" deleted successfully");
					else if( !found )
						System.out.println("Unable to find \""+filename+"\"");
					else if( !deleted ) 
						System.out.println("Failed to delete \""+filename+"\"");
					
			}
			else if( list.size() == 1 ) {
				for(RemoteFileInfo entry : list) {
					if( !mdelete( entry ) ) {
						System.out.println("Failed to delete filename \""+entry.name+"\"");
						return;
					}
					System.out.println("Filename \""+entry.name+"\" deleted successfully");
				}
			}
			
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	/* Delete multiple files in case entry is a directory
	 */
	public synchronized boolean mdelete(RemoteFileInfo entry) {
		if( entry.dir ) {
			cwd( entry.name );
			List<RemoteFileInfo> list = parse( list(".") );
			for(RemoteFileInfo listentry : list) {
				mdelete(listentry);
			}
			cwd("..");
			if( !rmdir( entry.name ) ) {
				System.out.println("Deletion of directory \""+entry.name+"\" failed!");
				return false;
			}
			return true;
		}
		else {
			if( !delete( entry.name ) ) {
				System.out.println("Deletion of file \""+entry.name+"\" failed!");
				return false;
			}
			return true;
		}
	}
	
	public synchronized boolean delete(String filename) {
		String response = null;
		
		out.println("DELE " + filename);
		
		response = readLine();
		return ( response.startsWith("250") );
		
	}
	
	
	public void cwdUI() {
		String dirname, socketInput;
		try {
			System.out.print("Enter directory name: ");
			dirname = reader.readLine();
			dbg(null, "Read: "+dirname);
			
			if( cwd(dirname) )
				System.out.println("Directory changed successfully!");
			else
				System.out.println("Directory change failed!");
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public void pwdUI() {
		String dirname, socketInput;
		String pwdInfo = pwd();
		System.out.println("PWD: "+pwdInfo);
	}
	
	public synchronized String pwd() {
		String response = null, pwdInfo = null;
		int first, second;
		
		out.println("PWD");
		
		
		response = readLine();
		if( response.startsWith("257") ) {
			first = response.indexOf("\"");
			second = response.indexOf("\"", first + 1);
			if(second > 0) {
				pwdInfo = response.substring(first + 1, second);
			}
		}
		
		return pwdInfo;
	}
	
	public void renameUI() {
		try {
			System.out.print("Enter file or directory to rename: ");
			String from = reader.readLine();
			System.out.print("Enter new name: ");
			String to = reader.readLine();
			
			if( rename(from, to) )
				System.out.println("Rename successfull");
			else
				System.out.println("Rename failed!");
		} catch(IOException ex) {
			ex.printStackTrace();
			return;
		}
	}
	
	public synchronized boolean rename(String from, String to) {
		String response = null;
		
		out.println("RNFR " + from);
		
		response = readLine();
		if( response.startsWith("350") ) {
			out.println("RNTO " + to);
			
			response = readLine();
			return ( response.startsWith("250") );
		}
		else {
			return false;
		}
	}
	
	public void helpUI() {
		System.out.println("OPTIONS:\n\tLOGIN\tQUIT\tLIST\tUPLOAD\tDOWNLOAD\n\tMKD\tRMD\tCWD\tPWD\tDEL");
	}
	
	public synchronized void checkInput(String command) {
		switch(command.toUpperCase()) {
			case "HELP" :
				helpUI();
				break;
			case "CONNECT" :
				bindUI(null);
				break;
			case "LOGIN" :
				loginUI();
				break;
			case "UPLOAD" :
				uploadUI();
				break;
			case "DOWNLOAD" :
				downloadUI();
				break;
			case "CWD" :
			case "CD" :
				cwdUI();
				break;
			case "PWD" :
				pwdUI();
				break;
			case "LIST" :
				listUI();
				break;
			case "MKD" :
			case "MKDIR" :
				mkdirUI();
				break;
			case "RMD" :
			case "RMDIR" :
				rmdirUI();
				break;
			case "DEL" :
			case "DELE" :
			case "DELETE" :
			case "DLT" :
				deleteUI();
				break;
			case "RENAME":
			case "RNM":
				renameUI();
				break;
			case "QUIT" :
				System.out.println("Bye bye...");
				System.exit(1);
				break;
			default :
				System.out.println("ERROR: Unknown command \""+command+"\"");
		}
	}
	
	public static void main(String [] args) {
		FtpClient client = new FtpClient(true, true);
		client.bindUI(args);
		client.loginUI();
		System.out.print("$> ");
		try {
			String userInput;
			while( true ) {
				if( client.reader.ready() ) {        
					userInput = client.reader.readLine();
					while( userInput.indexOf(' ') == 0 ) {
						userInput = userInput.substring(1);
					}
					if( userInput.indexOf(' ') < 0 ) {
						client.checkInput(userInput);
					}
					if( userInput.indexOf(' ') > 0 ) {
						client.checkInput( userInput.substring(0, userInput.indexOf(' ')) );
					}
					System.out.print("$> ");
				}
				else {
					Thread.sleep(500);
				}
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
	}
}