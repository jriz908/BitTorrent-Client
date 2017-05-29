package GivenTools;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


//This class listens to incoming connections that want to request pieces we have downloaded
public class IncomingPeer implements Runnable{
	
	public IncomingPeer () {

	}

	@Override
	public void run() {
		//stalls until user starts download
		while (!RUBTClient.getStart()) {
			
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				
			}
		}
		
		ServerSocket svc = null;
		try {
			svc = new ServerSocket(6881, 10);
			while (true) { //maybe not while true?
		    	// a "blocking" call which waits until a connection is requested	
				Socket incomingPeerSocket = svc.accept(); 
				
				System.out.println("Detected an incoming peer");
				
		    	//Peer constructor - byte[] id, String ip, int port, int threadID
		    	Peer incomingPeer = new Peer(null, incomingPeerSocket.getInetAddress().getHostAddress(), incomingPeerSocket.getPort(), RUBTClient.getThreadID());  
		    	incomingPeer.setPeerSocket(incomingPeerSocket);
		    	System.out.println(incomingPeerSocket.getInetAddress().getHostAddress());
		    	System.out.println(incomingPeerSocket.getPort());
		    	Thread incoming_thread = new Thread(incomingPeer);
		    	incoming_thread.start();
		    	
		    	RUBTClient.setThreadID(RUBTClient.getThreadID() + 1);
			}
		} catch (IOException e) {
			System.err.print("Incoming connection error in IncomingPeer.java, run(): " + e);
		} finally {
			try {
				svc.close();
			} catch (IOException e) {
				System.err.print("Failed to close server socket: " + e);
			}
		}
		
	}
	
	

}
