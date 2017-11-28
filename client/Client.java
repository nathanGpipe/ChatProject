import java.util.Scanner;
import java.util.ArrayList;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

class Client {
	
	private String helpscreen;
	
	private SocketChannel sc;

	private ByteBuffer buffer;
	
	private boolean admin;
	
	private String users;
	
	private String username;
	
	public Client(SocketChannel sc, String username) throws IOException {
		this.sc = sc;
		this.username = username;
		
		helpscreen = "Simply type a message you would like to send.\n"
					+"Preface commands with \"~~ \", avalible commands are:\n"
					+"\tkick <username> - Removes the specified user from the chat channel\n"
					+"\tlist - Provides a list of all connected users\n"
					+"\texit - Logs out of the chat channel and exits the program\n"
					+"\twhisper <username> <message> - sends a message privately to a single user\n";
					
		buffer = ByteBuffer.allocate(4096);
		
		sc.configureBlocking(true);
		//first contact
		//	sends the username
		//	admin privileges are allowed iff a 1 sent back
		//	a 0 indicates the connection was successful as a normal user
		//	a -1 indicates that the username was already in use
		buffer.put(username.getBytes());
		buffer.flip();
		sc.write(buffer);
		
		buffer.clear();
		
		sc.read(buffer);
		buffer.flip();
		int adminCode = buffer.getInt();
		admin = (adminCode == 1);
		if(admin) {
			System.out.println("--- Administrative privlages enabled ---");
		}
		
		buffer.clear();
		//end first contact
		
		sc.configureBlocking(false);
		if(adminCode == -1) {
			System.out.println("Invalid username");
			sc.close();
		}
	}
	
	//checks validity of messages before sending them to the server
	public void process(String msg) throws IOException {
		if(msg.equals("help")) { 				// Help screen
			System.out.println(helpscreen);
		} else {								// Communicate with server
			String[] words = msg.split(" ");
			if(words[0].equals("~~")) {// Command processing
				//kick
				if(words.length == 3 && words[1].equals("kick") && admin) {
					send(msg);
				//list
				} else if(words.length == 2 && words[1].equals("list")) {
					send(msg);
					
				//exit
				} else if(words.length == 2 && words[1].equals("exit")) {
					send(msg);
					sc.close();
				//whisper
				} else if(words.length > 2 && words[1].equals("whisper")) {
					send(msg);
				}
			} else {							// Simple message
				send(msg);
			}
		}
	}
	
	//send string
	private void send(String msg) throws IOException {
		buffer = ByteBuffer.allocate(4096);
		buffer.put(msg.getBytes());
		buffer.flip();
		sc.write(buffer);
		buffer.clear();
	}
	
	public static void main(String[] args){
		try {
			Console kb = System.console();
			SocketChannel sc = SocketChannel.open();
		
			while(!sc.isConnected()) {
				try {
					System.out.print("Address? ");
					String ip = kb.readLine();
					System.out.print("Port? ");
					int port = Integer.parseInt(kb.readLine());
	
					sc.connect(new InetSocketAddress(ip, port));
				} catch(NumberFormatException e) {
					System.out.println("invalid port");
				} catch(Exception e) {
					System.out.println("invalid ip/port combination");
				}
			}
		
			System.out.print("Username? ");
			String name = kb.readLine();
		
			Client c = new Client(sc, name);
			String input;
			
			ListenThread listener = new ListenThread(sc);
			listener.start();
			
			System.out.print("> ");
			while(sc.isConnected()) {
				input = "";
				
				input = kb.readLine();
				if(input != null) {
					System.out.print("> ");
					c.process(input);
				}
			}
			
			System.out.println("Connection closed");
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
}

class ListenThread extends Thread {
	
	private SocketChannel sc;
	
	private ByteBuffer buffer;
	
	//recieve string
	private String recieve() throws IOException {
		buffer = ByteBuffer.allocate(4096);
		int bytesRead = sc.read(buffer);
		buffer.flip();
		if(bytesRead > 0) {
			String temp = new String(buffer.array()).trim();
			buffer.clear();
			return temp;
		}
		buffer.clear();
		return null;
	}
	
	public ListenThread(SocketChannel s) {
		sc = s;
		buffer = ByteBuffer.allocate(4096);
	}
	
	public void run() {
		try {
			while(sc.isConnected()) {
				String recieved = recieve();
				if(recieved != null) {
					if(recieved.equals("~~ * kicked")) {
						System.out.println("--- Kicked by administrator ---");
						sc.close();
					} else {
						System.out.println(recieved);
						System.out.print("> ");
					}
				}
			}
		} catch (IOException e) {
			System.out.println("Exception found: " + e);
		}
	}
	
}
