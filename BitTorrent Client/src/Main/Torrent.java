//Jacob Rizer

package Main;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
	public final byte[] INTERESTED = new byte[]{0, 0, 0, 1, 2};
	public final byte[] UNCHOKE = new byte[]{0, 0, 0, 1, 1};
	public final byte[] REQUEST = new byte[]{0, 0, 0, 13, 6};
	public final byte[] PIECE = new byte[]{0, 0, 0, 0, 7};
	
	
	
	
	
	
	private TorrentInfo torrentInfo;
	
	private String fileNameToCreate;
	
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
	
	
	public Torrent(TorrentInfo ti, String fileName){
		this.torrentInfo = ti;
		this.fileNameToCreate = fileName;
		this.peerID = generateRandomID();
		this.infoHash = encodeInfoHash(torrentInfo.info_hash.array());
		this.downloaded = 0;
		this.uploaded = 0;
		this.left = torrentInfo.file_length;
		this.event = "started";
		
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
						   "&left=" + this.left + 
						   "&event=" + event;	
		
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
	
	//for each peer in the list of authorized -RU peers, download from them
	public void start() throws UnsupportedEncodingException{
		for(Peer p : peers){
			download(p);
		}
	}
	
	public void close() throws IOException{
		input.close();
		output.close();
		socket.close();
	}
	
	//begin the process of downloading from peer
	public void download(Peer peer) throws UnsupportedEncodingException{
		
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
	    ByteBuffer responseBuffer = ByteBuffer.wrap(response);
	    
	    byte[] infoHashArray = new byte[20];
	    
	    for(int i = 0; i < 20; i++){
	    	infoHashArray[i] = responseBuffer.get(28 + i);
	    }
	    
	    String responseInfoHash = encodeInfoHash(infoHashArray);
	    
	    if(!responseInfoHash.equals(infoHash)){
	    	System.out.println("INFO HASH DOES NOT MATCH");
	    	return;
	    }
	    
	    //send interested message
	    try {
			sendInterested();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	    
	    //read interested message
	    response = new byte[5];
	    try {
			input.readFully(response);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	    
	    System.out.println(checkArrayEquality(response, INTERESTED));
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
	    
		
		
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
	
	//send an unchoke message to the peer
	public void sendUnchoke() throws IOException{
		ByteBuffer buffer = ByteBuffer.wrap(UNCHOKE);
		sendMessage(buffer);
	}
	
	//send an interested message to the peer
	public void sendInterested() throws IOException{
		ByteBuffer buffer = ByteBuffer.wrap(INTERESTED);
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
