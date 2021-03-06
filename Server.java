import java.util.Scanner;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;

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
			e.printStackTrace();
		}
	}

}

class TcpServerThread extends Thread {

	private SocketChannel sc;

	private ConcurrentHashMap<String, CopyOnWriteArrayList<String>> users;
	
	private ByteBuffer buffer;
	
	private String username;
	
	private cryptotest crypt;
	
	private SecretKey secKey;
	
	private IvParameterSpec iv;

	public TcpServerThread(SocketChannel s, ConcurrentHashMap<String, CopyOnWriteArrayList<String>> u) throws IOException {
		sc = s;
		users = u;
		
		crypt = new cryptotest();
		crypt.setPublicKey("RSApub.der");
		crypt.setPrivateKey("RSApriv.der");
		
		
		buffer = ByteBuffer.allocate(4096);
		
		sc.configureBlocking(true);
		//first contact
		
			//RSA
		sc.read(buffer);
		buffer.flip();
		
		byte[] encrypted_keyBytes = new byte[buffer.remaining()];
		buffer.get(encrypted_keyBytes);
		byte[] keyBytes = crypt.RSADecrypt(encrypted_keyBytes);
		
		secKey = new SecretKeySpec(keyBytes, "AES");
		
		buffer.clear();
		buffer.putInt(1);
		buffer.flip();
		sc.write(buffer);
		buffer.clear();
		
			//AES
		sc.read(buffer);
		buffer.flip();
		
		byte[] encrypted_ivbytes = new byte[buffer.remaining()];
		buffer.get(encrypted_ivbytes);
		byte[] ivbytes = crypt.RSADecrypt(encrypted_ivbytes);
		//for(byte b : ivbytes) {
		//	System.out.print("" + b + " ");
		//}
		//System.out.println("");
		
		iv = new IvParameterSpec(ivbytes);
		buffer.clear();
		
			//username
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
		sc.configureBlocking(false);
		
	}

	public void run(){
		System.out.println("Session started with username " + username);
		try{
			String msg = "";
			String out = "";
			int messageCount = users.get("~all").size();
			int whisperCount = users.get(username).size();
		   	
			while(sc.isConnected()) {
				if(users.containsKey(username)) {
					msg = recieve();
					if(msg != null) {
						System.out.println(username + ": " + msg);
				
				
						String[] words = msg.split(" ");
						if(words[0].equals("~~")) {// Command processing
							//kick
							if(words.length == 3 && words[1].equals("kick") && username.equals("admin")) {
								if(users.containsKey(words[2])) {
									users.remove(words[2]);
								}
							//list
							} else if(words.length == 2 && words[1].equals("list")) {
								String userlist = "Connected users: ";
								for(String s : users.keySet()) {
									if(!(s.equals("~all"))) {
										userlist += "\n" + s;
									}
								}
								
								send(userlist);
								if(users != null) {
									System.out.println(users);
								}
							//exit
							} else if(words.length == 2 && words[1].equals("exit")) {
								sc.close();
								users.remove(username);
							//whisper
							} else if(words.length > 2 && words[1].equals("whisper")) {
								String key = "";
								System.out.println(username + " whispered to " + words[2]);
								for(String k : users.keySet()) {
									if(k.equals(words[2])) {
										try {
											users.get(words[2]).add("** " + username + ": " + msg.substring(11+words[2].length()));
										} catch (Exception e) {
											
										}
										
									}
								}
								
							}
						} else {
							users.get("~all").add(username + ": " + msg);
							messageCount++;
						}
					
						msg = "";
					} else if(users.containsKey(username)){
						int tmpAll = users.get("~all").size();
						int tmpWhisper = users.get(username).size();
						
						if(tmpAll > messageCount) {
							System.out.println(username + ": updating messages");
							for(int i = messageCount; i < tmpAll; i++) {
								send(users.get("~all").get(i) + "\n");
							}
							messageCount = tmpAll;
						} else if(tmpWhisper > whisperCount) {
							System.out.println(username + ": updating whispers");
							for(int i = whisperCount; i < tmpWhisper; i++) {
								send(users.get(username).get(i) + "\n");
							}
							whisperCount = tmpWhisper;
						}
					}
				} else {
					System.out.println("Client " + username + " was kicked");
					send("~~ * kicked");
					sc.close();
				}
			}
			
			System.out.println("Connection closed");
			
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
		byte[] msgBytes = crypt.encrypt(msg.getBytes(), secKey, iv);
		buffer.put(msgBytes);
		buffer.flip();
		sc.write(buffer);
		buffer.clear();
	}
	
	private String recieve() throws IOException {
		buffer = ByteBuffer.allocate(4096);
		int bytesRead = sc.read(buffer);
		
		//System.out.println("recieved " + buffer.remaining() + " bits");
		if(bytesRead > 0) {
			buffer.flip();
			byte[] encrypted_msgBytes = new byte[buffer.remaining()];
			buffer.get(encrypted_msgBytes);
			byte[] msgBytes = crypt.decrypt(encrypted_msgBytes, secKey, iv);
			buffer.clear();
			return new String(msgBytes).trim();
		}
		return null;
	}
}
