//Jacob Rizer

package Main;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.net.InetAddress;

import GivenTools.*;

public class Torrent {
	
	
	public static final ByteBuffer PEERS = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', 's' });
	public static final ByteBuffer INTERVAL = ByteBuffer.wrap(new byte[] { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public static final ByteBuffer PEER_ID = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', ' ', 'i', 'd' });
	public static final ByteBuffer IP = ByteBuffer.wrap(new byte[] { 'i', 'p' });
	public static final ByteBuffer PORT = ByteBuffer.wrap(new byte[] { 'p', 'o', 'r', 't' });
	
	//for handshake
	public static final byte[] HEADER = new byte[]{'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't', ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o', 'l'};
	public static final byte[] ZEROES = new byte[]{'0', '0', '0', '0', '0', '0', '0', '0'};
	
	//messages to peer
	public static final ByteBuffer KEEP_ALIVE = ByteBuffer.wrap(new byte[]{0, 0, 0, 0});
	public static final ByteBuffer INTERESTED = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 2});
	public static final ByteBuffer UNINTERESTED = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 3});
	public static final ByteBuffer CHOKE = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 0});
	public static final ByteBuffer UNCHOKE = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 1});
	public static final ByteBuffer REQUEST = ByteBuffer.wrap(new byte[]{0, 0, 0, 13, 6});
	public static final ByteBuffer PIECE = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, 7});
	
	public static final int LENGTH = 16384 ;
	
	
	
	
	private TorrentInfo torrentInfo;
	
	
	private FileOutputStream fos;
	
	private int port = 6881;
	private int downloaded;
	private int uploaded;
	private int left;
	private String event;
	private String peerID;
	private String infoHash;
	
	private URL trackerURL;
	
	private Map<ByteBuffer,Object> trackerResponse;
	private List<Peer> peers;
	
	private Socket socket;
	private DataInputStream input;
	private DataOutputStream output;
	
	private ByteBuffer responseBuffer;
	private ByteBuffer requestBuffer;
	
	private int index = 0;
	private int numBlocks;
	private boolean firstBlock;
	
	
	
	public Torrent(TorrentInfo ti, String fileName) throws FileNotFoundException{
		this.torrentInfo = ti;
		this.fos = new FileOutputStream(new File(fileName));
		this.peerID = generateRandomID();
		this.infoHash = encodeInfoHash(torrentInfo.info_hash.array());
		this.downloaded = 0;
		this.uploaded = 0;
		this.left = torrentInfo.file_length;
		this.event = "";
		
		this.numBlocks = ti.piece_length/LENGTH;
		firstBlock = true;
		
		this.requestBuffer = ByteBuffer.allocate(17);
		
		peers = new ArrayList<Peer>();
		
	}
	
	//set the tracker URL that will be used to create a connection with the tracker
	public void setTrackerURL() throws MalformedURLException{
		String urlString = this.torrentInfo.announce_url.toString() + 
						   "?info_hash=" + this.infoHash + 
						   "&peer_id=" + this.peerID + 
						   "&port=" + this.port + 
						   "&uploaded=" + this.uploaded + 
						   "&downloaded=" + this.downloaded + 
						   "&left=" + this.left;	
		
		System.out.println(urlString);
		trackerURL = new URL(urlString);
	}
	
	public void setTrackerURLEventStarted() throws MalformedURLException{
		
		this.event = "started";
		
		String urlString = this.torrentInfo.announce_url.toString() + 
						   "?info_hash=" + this.infoHash + 
						   "&peer_id=" + this.peerID + 
						   "&port=" + this.port + 
						   "&uploaded=" + this.uploaded + 
						   "&downloaded=" + this.downloaded + 
						   "&left=" + this.left + 
						   "&event=" + this.event;	
		
		System.out.println(urlString);
		trackerURL = new URL(urlString);
	}
	
	public void setTrackerURLEventCompleted() throws MalformedURLException{
		
		this.event = "completed";
		
		String urlString = this.torrentInfo.announce_url.toString() + 
						   "?info_hash=" + this.infoHash + 
						   "&peer_id=" + this.peerID + 
						   "&port=" + this.port + 
						   "&uploaded=" + this.uploaded + 
						   "&downloaded=" + this.downloaded + 
						   "&left=" + this.left + 
						   "&event=" + this.event;	
		
		System.out.println(urlString);
		trackerURL = new URL(urlString);
	}
	
	public void setTrackerURLEventStopped() throws MalformedURLException{
		
		this.event = "stopped";
		
		String urlString = this.torrentInfo.announce_url.toString() + 
						   "?info_hash=" + this.infoHash + 
						   "&peer_id=" + this.peerID + 
						   "&port=" + this.port + 
						   "&uploaded=" + this.uploaded + 
						   "&downloaded=" + this.downloaded + 
						   "&left=" + this.left + 
						   "&event=" + this.event;	
		
		System.out.println(urlString);
		trackerURL = new URL(urlString);
	}
	
	//send a request to the tracker to get the list of peers
	@SuppressWarnings("unchecked")
	public void sendRequestToTracker() throws IOException, BencodingException{
		
		HttpURLConnection conn = (HttpURLConnection) trackerURL.openConnection();
		conn.setRequestMethod("GET");
		DataInputStream dataStream = new DataInputStream(conn.getInputStream());
		
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		
		int num;
		while ((num = dataStream.read()) != -1) {
			byteStream.write(num);
		}
		dataStream.close();
		
		trackerResponse = (Map<ByteBuffer, Object>) Bencoder2.decode(byteStream.toByteArray());
		
		byteStream.close();
		
	   
	}
	
	//iterate through list of peers received from trackers to obtain the ones that have the -RU prefix
	public void setPeers() throws UnsupportedEncodingException{
		
		List trackerPeers = (List) trackerResponse.get(PEERS);
		
		for(int i = 0; i < trackerPeers.size(); i++){
			Map map = (Map) trackerPeers.get(i);
			ByteBuffer idBuffer = (ByteBuffer) map.get(PEER_ID);
			
			String peer_id = new String(idBuffer.array(), "ASCII");
			
			if(peer_id.startsWith("-RU")){
				
				ByteBuffer ipBuffer = (ByteBuffer) map.get(IP);
				String ip = new String(ipBuffer.array(), "ASCII");
				
				int port = (int) map.get(PORT);
				
				Peer peer = new Peer(peer_id, ip, port);
				peers.add(peer);
			}
			
		}
	}
	
	//download from fastest peer
	public void start() throws IOException, BencodingException, InterruptedException{
		//Peer p = getFastestPeer(peers);
		Peer p = peers.get(0);
		
		download(p);
	}
	
	public void close() throws IOException, BencodingException{
		
		setTrackerURLEventStopped();
		sendRequestToTracker();
		
		input.close();
		output.close();
		socket.close();
	}
	
	public void writeToFile(byte[] piece) throws IOException{
		fos.write(piece);
	}
	
	public Peer getFastestPeer(List<Peer> peers){
		
		double min = Double.MAX_VALUE;
		double time = 0;
		double avg = 0;
		Peer result = null;
		
		for(Peer p : peers){
			
			for(int i = 0; i < 10; i++){
				
				double test = pingPeer(p);
				
				if(test < 0){
					i--;
					continue;
				}
				
				time += test;
				
			}
			
			avg = time / 10;
			
			System.out.println("Average ping for " + p.getPeer_ID() + " is: " + avg);
			
			if(avg < min){
				result = p;
				min = avg;
			}
			
		}
		
		System.out.println(result.getPeer_ID());
		
		return result;
		
		
	}
	
	public double pingPeer(Peer peer){
		
		double result = 0;
		
		try {
		      InetAddress inet = InetAddress.getByName(peer.getIP());
		 
		      long finish = 0;
		      long start = new GregorianCalendar().getTimeInMillis();
		 
		      if (inet.isReachable(5000)){
		        finish = new GregorianCalendar().getTimeInMillis();
		        result = finish - start;
		        System.out.println("Ping RTT: " + (result + "ms"));
		      } else {
		        System.out.println(peer.getIP() + " NOT reachable.");
		        return -1;
		      }
		    } catch ( Exception e ) {
		      System.out.println("Exception:" + e.getMessage());
		    }
		
		
		return result;
	}
	
	//begin the process of downloading from peer
	public void download(Peer peer) throws IOException, BencodingException, EOFException, InterruptedException{

		String ip = peer.getIP();
		int port = peer.getPort();


		try {
			socket = new Socket(ip, port);
		}
		catch (IOException e) {
			System.out.println(e);
			return;
		}


		try {
			input = new DataInputStream(socket.getInputStream());
		}
		catch (IOException e) {
			System.out.println(e);
			return;
		}


		try {
			output = new DataOutputStream(socket.getOutputStream());
		}
		catch (IOException e) {
			System.out.println(e);
			return;
		}

		//handshake
		try {
			sendHandshake();
		}
		catch (IOException e) {
			System.out.println(e);
			return;
		}

		//read handshake
		byte[] response = new byte[68];
		try {

			input.readFully(response);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		//check handshake ID
		String responseString = new String(response, "ASCII");

		String responsePeerID = responseString.substring(48);


		if(!responsePeerID.equals(peer.getPeer_ID())){
			System.out.println("PEER ID DOES NOT MATCH");
			return;
		}

		//check info_hash
		responseBuffer = ByteBuffer.wrap(response);

		byte[] infoHashArray = new byte[20];

		for(int i = 0; i < 20; i++){
			infoHashArray[i] = responseBuffer.get(28 + i);
		}

		String responseInfoHash = encodeInfoHash(infoHashArray);

		if(!responseInfoHash.equals(infoHash)){
			System.out.println("INFO HASH DOES NOT MATCH");
			return;
		}

		System.out.println("HANDSHAKE COMPLETE");
		//ToolKit.print(responseBuffer);

		responseBuffer.clear();



		try {
			input.readFully(response);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		//DISCARD BITFIELD

		//send interested

		byte[] messages = new byte[5];
		responseBuffer = ByteBuffer.wrap(messages);

		try {
			sendInterested();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		try {
			input.readFully(messages);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}



		if(messages[4] == 1){
			System.out.println("Unchoke complete");
			peer.setUnchoked(true);
		}

		trackerResponse.clear();

		setTrackerURLEventStarted();
		sendRequestToTracker();

		//printTrackerResponse();

		byte[] messages2 = new byte[13];
		ByteBuffer messagesBuffer = ByteBuffer.wrap(messages2);

		byte[] fileArray = new byte[torrentInfo.file_length];
		ByteBuffer fileBuffer = ByteBuffer.wrap(fileArray);

		System.out.println(torrentInfo.file_length);
		
		System.out.println("Starting download...");
		
		long startTime = System.currentTimeMillis();

		while(left > 0){

			try {
				sendRequest();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			Thread.sleep(120);

			try {
				System.out.println(input.readFully(messages2));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}


			//int size = messagesBuffer.getInt();

			/*
		    	System.out.println(size);
		    	System.out.println(messagesBuffer.get());
		    	System.out.println(messagesBuffer.get());
		    	System.out.println(messagesBuffer.getInt());
		    	System.out.println(messagesBuffer.getInt());
			 */

			int bytesRead;

			try {
				if(left < LENGTH)
					 bytesRead = input.read(fileArray, 0, left);
				else
					 bytesRead = input.read(fileArray);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		
			this.downloaded += bytesRead;
			this.left -= bytesRead;

			System.out.println("Downloaded: " + this.downloaded + "   Index: " + index + "    First block? " + this.firstBlock);
			
			messagesBuffer.clear();




			this.firstBlock = !this.firstBlock;

			if(this.firstBlock){
				index++;
				//System.out.println("index++");
			}

		}
		
		long downloadTime = System.currentTimeMillis() - startTime;
		
		System.out.println("Download finished in " + downloadTime + "ms.");
		
		try {
			writeToFile(fileArray);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		
		setTrackerURLEventCompleted();
		sendRequestToTracker();
		
		



	}

	//for checking responses from peer
	public boolean checkArrayEquality(byte[] one, byte[] two){
		
		if (one.length != two.length){
			return false;
		}
		
		for(int i = 0; i < one.length; i++){
			if(one[i] != two[i]){
				System.out.println(one[i] + " : " + two[i]);
				return false;
			}
		}
		
		return true;
	}
	
	//send a handshake to the peer
	public void sendHandshake() throws IOException{
		ByteBuffer handshake = ByteBuffer.allocate(68);
		
		handshake.put((byte) 19);
		handshake.put(HEADER);
		handshake.put(ZEROES);
		
		
		
		handshake.put(torrentInfo.info_hash);
		handshake.put(peerID.getBytes());
		
		//ToolKit.print(handshake);
		
		sendMessage(handshake);
		
	}
	
	public void sendHave() throws IOException{
		
		ByteBuffer buffer = ByteBuffer.allocate(9);
		
		buffer.putInt(5);
		buffer.put((byte) 4);
		buffer.putInt(index);
		
		sendMessage(buffer);
		
	}
	
	public void sendRequest() throws IOException{
		
		int length; 
		
		if(left < LENGTH){
			length = left;
		}else{
			length = LENGTH;
		}
			

		requestBuffer.put(new byte[]{0, 0, 0, 13, 6});

		requestBuffer.putInt(index);

		if(firstBlock)
			requestBuffer.putInt(0);
		else
			requestBuffer.putInt(LENGTH);
		
		requestBuffer.putInt(length);

		sendMessage(requestBuffer);
		
		requestBuffer.clear();
		
	}
	
	//send an unchoke message to the peer
	public void sendUnchoke() throws IOException{
		ByteBuffer buffer = UNCHOKE;
		sendMessage(buffer);
	}
	
	//send an interested message to the peer
	public void sendInterested() throws IOException{
		ByteBuffer buffer = INTERESTED;
		sendMessage(buffer);
	}
	
	//method for sending some message to peer
	public void sendMessage(ByteBuffer message) throws IOException{
		if(!socket.isConnected()){
			System.out.println("socket not connected");
			return;
		}
		
		output.write(message.array());
	}
	
	
	//For debugging. Printing the map that is received from the tracker
	public void printTrackerResponse(){
		ToolKit.print(trackerResponse);
	}
	
	//generate a random 20 byte/char ID to be my peer ID
	private String generateRandomID(){
		
		StringBuilder sb = new StringBuilder(20);
		Random random = new Random();
		
		//to ensure that peer ID doesn't start with RU the first character will be x
		sb.append('x');
		
		//next 19 characters are generated randomly
		for(int i = 0; i < 19; i++){
			char c = (char)(random.nextInt(26) + 'a');
			sb.append(c);
		}
		
		//System.out.println(sb.toString());
		return sb.toString();
		
		
	}
	
	
	//Encodes the info_hash value from the torrent info into the hex string that is appended to the URL
	private String encodeInfoHash(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		final Formatter formatter = new Formatter(sb);
		for (byte b : bytes) {
			sb.append("%");
			formatter.format("%02x", b);
		}
		
		return sb.toString().toUpperCase();
	}
	



}