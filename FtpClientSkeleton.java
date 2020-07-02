package ce325.hw2;

import java.util.*;
import java.io.*;
import java.net.*;

public class FtpClientSkeleton {  
	Socket controlSocket;
	BufferedReader reader;
	PrintWriter out;
	BufferedReader in;  
	File workingDir;
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
	
	public FtpClientSkeleton(boolean pasv, boolean overwrite) {
		passive = pasv;
		this.overwrite = overwrite;
		reader = new BufferedReader( new InputStreamReader(System.in) );
		workingDir = new File(".");
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
	
	public boolean bind(String inetAddress, int port) {
		
		try {
			controlSocket = new Socket(InetAddress.getByName(inetAddress), port);
			out = new PrintWriter(controlSocket.getInputStream(), true);
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
		
	}
	
	public void loginUI() {    
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
				System.out.println("Login for user \""+username+"\"Failed!");
			
		} catch(IOException ex) {
			ex.printStackTrace();
		}    
	}
	
	public boolean login(String username, String passwd) {
		PrintWriter out = new PrintWriter(controlSocket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		String response;
		
		response = in.readLine();
		if( !response.startsWith("220") ) {
			throw new IOException("Cannot connect to server");
			//dbg(null, "Cannot connect to server");
		}
		
		try {
			out.println(username);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		//Pare apantisi apo ton server gia to username
		//An i apantisi pou tha erthei den einai 331 exei ginei kapoio lathos
		response = in.readLine();
		if( !response.startsWith("331") ) {
			throw new Exception("User does not exist");
			return false;
		}
		
		try {
			out.println(passwd);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		//Pare apantisi apo ton server gia to password
		//An ksekinaei me 530 exoume fail sto login
		response = in.readLine();
		if( response.startsWith("530") ) {
			throw new IOException("Login incorrect");
			return false;
		}
		
		return true; //Epityxia syndesis ston server
		
	}
	
	//Binary Mode gia tin apostoli arxeiwn
	public synchronized boolean binary() {
		binary = true;
		try {
			out.println("TYPE I");
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		String response = in.readLine();
		if( !response.startsWith("200") ){
			return false;
		}
		
		return true;
	}
	
	//Ascii mode gia tin apostoli arxeiwn
	public synchronized boolean ascii() {
		binary = false;
		try {
			out.println("TYPE A");
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		String response = in.readLine();
		if( !response.startsWith("200") ) {
			return false;
		}
		
		return true;
	}
	
	public synchronized void setupPassiveChannel() {
		
		try {
			out.println("PASV");
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		String response = in.readLine();
		if( !response.startsWith("227") ) {
			throw new IOException("FTP could not enter passive mode");
		}
		
		String ip = null;
		int begin = response.indexOf("(");
		int end = response.indexOf(")", begin + 1);
		
		String sub = orig.substring(begin + 1, end);
		String[] tokens = sub.split(",");
		
		//Pare tin IP apo to substring tis morfis (IP1,IP2,IP3,IP4,PORT-MSB,PORT-LSB)
		String ip = tokens[0];
		int i;
		for(i = 1; i < tokens.length - 2; i++)
			ip += "." + tokens[i];
		
		//Pare ton arithmo tou port apo ta (PORT-MSB, PORT-LSB)
		String portNumber = Integer.toHexString(Integer.parseInt(tokens[i])) + Integer.toHexString(Integer.parseInt(tokens[i+1]));
		int port = Integer.parseInt(portNumber, 16);
		
		/** Ksenika ena Thread?????? */
		
	}
	
	public synchronized void closePassiveChannel() {
		try {
			if(passive) {
				if(newConnSocket != null && !newConnSocket.isClosed()) {
					dbg(null, "Closing passive connection");
					oStream.flush();
					oStream.close();
					iStream.close();
					newConnSocket.close();
				}
			}
		}
		catch(IOException ex) {
			System.err.println("Error closing transfer channel: " + ex.getMessage());
		}
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
		
		try {
			out.println("PASV");
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		String response = in.readLine();
		if( !response.startsWith("227") ) {
			throw new IOException("FTP could not enter passive mode");
		}
		
		String ip = null;
		int begin = response.indexOf("(");
		int end = response.indexOf(")", begin + 1);
		
		String sub = orig.substring(begin + 1, end);
		String[] tokens = sub.split(",");
		
		//Pare tin IP apo to substring tis morfis (IP1,IP2,IP3,IP4,PORT-MSB,PORT-LSB)
		ip = tokens[0];
		int i;
		for(i = 1; i < tokens.length - 2; i++)
			ip += "." + tokens[i];
		
		//Pare ton arithmo tou port apo ta (PORT-MSB, PORT-LSB)
		String portNumber = Integer.toHexString(Integer.parseInt(tokens[i])) + Integer.toHexString(Integer.parseInt(tokens[i+1]));
		int port = Integer.parseInt(portNumber, 16);
		/** Stelnoume STRING h FILE? */
		try {
			out.println("LIST " + path);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		Socket newConnSocket = new Socket(InetAddress.getByName(ip), port);
		
		response = in.readLine(); //Pairnoume apantisi typou ls -l
		
		
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
		String name;
		String parentDir;
		
		public RemoteFileInfo(String line) {
		}
		
		private void permissions(String perms) {
		}
		
		public String toString() {      
		}
	}
	
	public List<RemoteFileInfo> parse(String info) {
		
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
	public void mupload(File f) {
		String filename = f.getName();
		BufferedInputStream iStream = new BufferedInputStream(new FileInputStream(filename));
		
		try {
			out.println("PASV");
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		String response = in.readLine();
		if( !response.startsWith("227") ) {
			throw new IOException("FTP could not enter passive mode");
		}
		
		String ip = null;
		int begin = response.indexOf("(");
		int end = response.indexOf(")", begin + 1);
		
		String sub = orig.substring(begin + 1, end);
		String[] tokens = sub.split(",");
		
		//Pare tin IP apo to substring tis morfis (IP1,IP2,IP3,IP4,PORT-MSB,PORT-LSB)
		String ip = tokens[0];
		int i;
		for(i = 1; i < tokens.length - 2; i++)
			ip += "." + tokens[i];
		
		//Pare ton arithmo tou port apo ta (PORT-MSB, PORT-LSB)
		String portNumber = Integer.toHexString(Integer.parseInt(tokens[i])) + Integer.toHexString(Integer.parseInt(tokens[i+1]));
		int port = Integer.parseInt(portNumber, 16);
		
		if( !binary() ) {
			throw new IOException("FTP Client could not enter Binary mode");
		}
		
		try {
			out.println("STOR " + filename);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		//Pleon exoume kai tin dieuthinsi IP kai to port
		//gia na ekkinisoume mia nea syndesi
		Socket newConnSocket = Socket(InetAddress.getByName(ip), port);
		
		response = in.readLine();
		if( !response.startsWith("150") ) {
			throw new IOException("FTP Client could not send file");
		}
		
		BufferedOutputStream oStream = new BufferedOutputStream(newConnSocket.getOutputStream());
		byte[] data = new byte[1024];
		int readBytes = 0;
		
		do {
			readBytes = iStream.read(data);
			oStream.write(data, 0, readBytes);
		}while(readBytes != -1);
		
		try {
			if(passive) {
				if(newConnSocket != null && !newConnSocket.isClosed()) {
					dbg(null, "Closing passive connection");
					oStream.flush();
					oStream.close();
					iStream.close();
					newConnSocket.close();
				}
			}
		}
		catch(IOException ex) {
			System.err.println("Error closing transfer channel: " + ex.getMessage());
		}
		
		response = in.readLine();
		if( !response.startsWith("226") ) {
			throw new IOException("Cannot complete transfer to FTP Server");
		}
		
	}
	
	public void downloadUI() {
		try {
			System.out.print("Enter file to download: ");
			String filename = reader.readLine();
			File file = new File(filename);                // we have an absolute path
			
			if( file.exists() && !file.isDirectory()) {
				System.out.println("File \""+file.getPath()+"\" already exist.");
				String yesno;
				do {
					System.out.print("Overwrite (y/n)? ");
					yesno = reader.readLine().toLowerCase();
				} while( !yesno.startsWith("y") && !yesno.startsWith("n") );
				if( yesno.startsWith("n") )
					return;
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
	public boolean mdownload(RemoteFileInfo entry) {
		String response = null;
		InputStream iStream = null;
		
		setupPassiveChannel();
		
		try {
			out.println("RETR " + entry.name);
			out.flush();
		}
		catch(IOException ex) {
			System.err.println("FTP could not request download.");
		}
		
		response = in.readLine();
		if( !response.startsWith("150") ) {
			dbg(null, "Invalid response from RETR command.");
			closePassiveChannel();
			return false;
		}
		try {
			
			if(passive) {
				iStream = newConnSocket.getInputStream();
			}
			
			if( binary() ) {
				int readBytes = 0;
				byte[] data = new byte[1024];
				
				FileOutputStream output = new FileOutputStream(entry.name);
				BufferedInputStream input = new BufferedInputStream(new InputStream(newConnSocket.getInputStream()));
				
				do {
					readBytes = input.read(data, 0, 1024);
					output.write(data, 0, readBytes);
				}while(readBytes != -1);
				
				try {
					output.close();
					input.close();
				}
				catch(IOException ex) {
					System.err.println("Cannot close buffers for download.");
				}
			}
		}
		catch(IOException ex) {
			System.err.println("Error getting file: " + ex.getMessage());
		}
		
		closePassiveChannel();
		response = in.readLine();
		return ( response.startsWith("226") );
		
	}
	
	/**
	 * Return values: 
	 *  0: success
	 * -1: File exists and cannot overwritten
	 * -2: download failure
	 */
	public int download(RemoteFileInfo entry) {
		
	}
	
	public boolean mkdir(String dirname) {
		PrintWriter out = new PrintWriter(controlSocket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		String response = null;
		
		try {
			out.println("MKD " + dirname);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
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
	
	public boolean rmdir(String dirname) {
		PrintWriter out = new PrintWriter(controlSocket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		String response = null;
		
		try{
			out.println("RMD "+ dirname);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		response = in.readLine();

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
	
	public boolean cwd(String dirname) {
		try {
			out.println("CWD " + dirname);
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		String response = in.readLine();
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
	public boolean mdelete(RemoteFileInfo entry) {
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
	
	public boolean delete(String filename) {
		try {
			out.println("DELE " + filename);
			out.flush();
		}
		catch(IOException ex) {
			System.err.println("FTP coul not delete file.");
		}
		
		String response = in.readLine();
		return (response.startsWith("250"));
		
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
	
	public String pwd() {
		String response = null, pwdInfo = null;
		int first, second;
		
		try {
			out.println("PWD");
			out.flush();
		}
		catch(IOException ex) {
			ex.printStackTrace();
		}
		
		if( response.startsWith("257") ) {
			first = response.indexOf("<");
			second = response.indexOf(">", first + 1);
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
	
	public boolean rename(String from, String to) {
		
	}
	
	public void helpUI() {
		System.out.println("OPTIONS:\n\tLOGIN\tQUIT\tLIST\tUPLOAD\tDOWNLOAD\n\tMKD\tRMD\tCWD\tPWD\tDEL");
	}
	
	public void checkInput(String command) {
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