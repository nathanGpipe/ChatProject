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
	
	public Client(SocketChannel sc) {
		this.sc = sc;
		
		helpscreen = "Simply type a message you would like to send.\n"
					+"Preface commands with \"~~ \", avalible commands are:\n"
					+"\tkick <username> - Removes the specified user from the chat channel\n"
					+"\tlist - Provides a list of all connected users\n"
					+"\texit - Logs out of the chat channel and exits the program\n"
					+"\twhisper <username> <message> - sends a message privately to a single user\n";
					
		buffer = ByteBuffer.allocate(4096);
		
		//check admin privileges
		//	sends 1, admin privileges are allowed iff
		//	a 1 is sent back as the response
		buffer.putInt(1);
		buffer.flip();
		sc.write(buffer);
		
		sc.read(buffer);
		buffer.flip();
		int adminCode = buffer.getInt();
		admin = (adminCode == 1);
		
		sc.configureBlocking(false);
	}
	
	//checks validity of messages before sending them to the server
	public void process(String msg) {
		if(msg.equals("help")) { 				// Help screen
			System.out.println(helpscreen);
		} else {								// Communicate with server
			String[] words = msg.split(" ");
			if(words[0].equals("~~") && admin) {// Command processing
				//kick
				if(words.length == 3 && words[1].equals("kick")) {
					send(msg);
				//list
				} else if(words.length == 2 && words[1].equals("list")) {
					sc.configureBlocking(true);
					send(msg);
					users = recieve();
					if(users != null) {
						System.out.println(users);
					}
					sc.configureBlocking(false);
				//exit
				} else if(words.length == 2 && words[1].equals("exit")) {
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
	
	private void send(String msg) {
		buffer.put(msg.getBytes());
		buffer.flip();
		sc.write(buffer);
	}
	
	private String recieve() {
		int bytesRead = sc.read(buffer);
		buffer.flip();
		if(bytesRead > 0) {
			return new String(buffer.toArray()).trim();
		}
		return null;
	}
	
	public static void main(String[] args){
		try {
			Scanner kb = new Scanner(System.in);
			SocketChannel sc = SocketChannel.open();
			
			while(!sc.isConnected()) {
				try {
					System.out.print("Address? ");
					String ip = kb.nextLine();
					System.out.print("Port? ");
					int port = Integer.parseInt(kb.nextLine());
		
					sc.connect(new InetSocketAddress(ip, port));
				} catch(NumberFormatException e) {
					System.out.println("invalid port");
				} catch(Exception e) {
					System.out.println("invalid ip/port combination");
				}
			}
		}
		
		Client c = new Client(sc);
		String input;
		
		while(sc.isConnected()) {
			if(kb.hasNext()) {
				input = kb.nextLine();
				System.out.print("> ");
				c.process(input);
			}
			
			String recieved = c.recieve();
			if(recieved != null) {
				System.out.println(recieved);
				System.out.print("> ");
			}
			
		}
		
		kb.close();
		
	}
}
