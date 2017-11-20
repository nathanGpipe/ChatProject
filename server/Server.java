import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;

class Server {
	
	public static ConcurrentHashMap<String, SocketAddress> users;
	
	public static void main(String[] args) {

		try {
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
			System.out.println("Exception found");
		}
	}

}

class TcpServerThread extends Thread {

	SocketChannel sc;

	ConcurrentHashMap<String, SocketAddress> users;
	
	ByteBuffer buffer;
	
	String username;

	public TcpServerThread(SocketChannel s, ConcurrentHashMap<String, SocketAddress> u) throws IOException {
		sc = s;
		users = u;
		
		buffer = ByteBuffer.allocate(4096);
		
		//first contact
		sc.read(buffer);
		buffer.flip();
		username = new String(buffer.array()).trim();
		buffer.clear();
		
		if(!users.containsKey(username)) {
			users.put(username, sc.getRemoteAddress());
			
			if(username.equals("admin")) {
				buffer.putInt(1);
			} else {
				buffer.putInt(0);
			}
		} else {
			buffer.putInt(-1);
		}
		buffer.flip();
		sc.write(buffer);
		
		buffer.clear();
		//end first contact
		
		sc.configureBlocking(true);
	}

	public void run(){
		System.out.println("Session started with username " + username);
		try{
			String in = "";
			String out = "";
		   	
			while(sc.isConnected()) {
				buffer = ByteBuffer.allocate(4096);
				sc.read(buffer);
				buffer.flip();
				in = new String(buffer.array()).trim();
				System.out.println(in);
				
				out = "";
				in = "";
			}
			System.out.println("Client " + username + " disconnected");
			sc.close();
		} catch(IOException e){
			System.out.println("Got an exception.");
		}
	
	}
}
