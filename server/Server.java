import java.util.Scanner;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;

class Server {
	
	
	public static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> users;
	
	public static void main(String[] args) {

		try {
			users = new ConcurrentHashMap<String, CopyOnWriteArrayList<String>>();
			users.put("~all", new CopyOnWriteArrayList<String>());
			
			ServerSocketChannel s = ServerSocketChannel.open();
			Scanner kb = new Scanner(System.in);
			System.out.print("Port? ");
			int port = -1;
			while(port == -1) {
				// handles port number errors
				try{
					port = Integer.parseInt(kb.nextLine());
					if(port < 0)
						throw new NumberFormatException();
					s.bind(new InetSocketAddress(port));
				} catch(NumberFormatException e){
					port = -1;
					System.out.println("Invalid port number.\n");
					System.out.print("Enter a valid port number: ");
				} catch(SocketException e) {
					port = -1;
					System.out.println("Permission Denied.\n");
					System.out.print("Enter a valid port number: ");
				} catch(IOException e) {
					port = -1;
					System.out.println(e);
				}
			}
			System.out.println("Waiting for connections...");

			while(true) {
				SocketChannel sc = s.accept();
				TcpServerThread t = new TcpServerThread(sc, users);
				t.start();
			}
		} catch(IOException e) {
			System.out.println("Exception found: " + e);
		}
	}

}

class TcpServerThread extends Thread {

	private SocketChannel sc;

	private ConcurrentHashMap<String, CopyOnWriteArrayList<String>> users;
	
	private ByteBuffer buffer;
	
	private String username;

	public TcpServerThread(SocketChannel s, ConcurrentHashMap<String, CopyOnWriteArrayList<String>> u) throws IOException {
		sc = s;
		users = u;
		
		buffer = ByteBuffer.allocate(4096);
		
		//first contact
		sc.configureBlocking(true);
		username = recieve();
		buffer.clear();
		
		if(!users.containsKey(username) && !username.equals("~all")) {
			users.put(username, new CopyOnWriteArrayList<String>());
			setName(username);
			
			if(username.equals("admin")) {
				buffer.putInt(1);
			} else {
				buffer.putInt(101);
			}
		} else {
			buffer.putInt(-1);
		}
		buffer.flip();
		sc.write(buffer);
		
		buffer.clear();
		//end first contact
		
		//sc.configureBlocking(true);
	}

	public void run(){
		System.out.println("Session started with username " + username);
		try{
			String msg = "";
			String out = "";
			int messageCount = 0;
		   	
			while(sc.isConnected()) {
				if(users.containsKey(username)) {
					msg = recieve();
					System.out.println(msg);
				
				
					String[] words = msg.split(" ");
					if(words[0].equals("~~")) {// Command processing
						//kick
						if(words.length == 3 && words[1].equals("kick") && username.equals("admin")) {
							if(users.containsKey(words[2])) {
								users.remove(words[2]);
							}
						//list
						} else if(words.length == 2 && words[1].equals("list")) {
							send(msg);
							if(users != null) {
								System.out.println(users);
							}
							//sc.configureBlocking(false);
						//exit
						} else if(words.length == 2 && words[1].equals("exit")) {
							sc.close();
							users.remove(username);
						//whisper
						} else if(words.length > 2 && words[1].equals("whisper")) {
							users.get(words[2]).add(username + " " + msg.substring(8+words[2].length()));
						}
					} else {
						users.get("~all").add(username + ": " + msg);
					}
					
					msg = "";
				} else {
					System.out.println("Client " + username + " kicked");
					send("~~ * kicked");
					sc.close();
				}
			}
		} catch(IOException e) {
			System.out.println("Got an exception.");
		} finally {
			if(users.containsKey(username)) {
				users.remove(username);
			}
		}
		
	}
	
	private void send(String msg) throws IOException {
		buffer = ByteBuffer.allocate(4096);
		buffer.put(msg.getBytes());
		buffer.flip();
		sc.write(buffer);
	}
	
	private String recieve() throws IOException {
		buffer = ByteBuffer.allocate(4096);
		int bytesRead = sc.read(buffer);
		buffer.flip();
		if(bytesRead > 0) {
			return new String(buffer.array()).trim();
		}
		return null;
	}
}
