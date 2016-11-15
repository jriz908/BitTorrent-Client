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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
	
	/**
	 * Some public static fields. Mainly
	 * literal bytebuffers that we use for communication.
	 */
	
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
	
	/**
	 * The length that we will use for one block.
	 * 2^14 bytes.
	 */
	public static final int LENGTH = 16384 ;
	
	
	
	/**
	 * The torrentInfo object obtained from the torrent file
	 */
	private TorrentInfo torrentInfo;
	
	/**
	 * Stream for writing bytes to the file
	 */
	private FileOutputStream fos;
	
	private int port = 6881;
	
	/**
	 * These will change as the download proceeds.
	 * Also used for the tracker messages.
	 */
	private int downloaded;
	private int uploaded;
	private int left;
	
	/**
	 * For tracker communication.
	 * The event field will change as the download
	 * progresses.
	 */
	private String event;
	private String peerID;
	private String infoHash;
	
	/**
	 * The URL that we use to contact the tracker.
	 */
	private URL trackerURL;
	
	/**
	 * The tracker response will contain a map
	 * as well as the list of peers we will download
	 * from.
	 */
	private Map<ByteBuffer,Object> trackerResponse;
	private List<Peer> peers;
	
	/**
	 * The socket we will use as well as the streams
	 * we will use to communicate with the peer.
	 */
	private Socket socket;
	private DataInputStream input;
	private DataOutputStream output;
	
	/**
	 * Some buffers for peer communication
	 */
	private ByteBuffer responseBuffer;
	private ByteBuffer requestBuffer;
	
	/**
	 * Piece index. Increments by 1 after each
	 * piece is successfully downloaded.
	 */
	private int index = 0;
	
	/**
	 * Whenever we get to a new piece we need
	 * to use this to advance the index number
	 * and verify the hash of a new completed
	 * piece.
	 */
	private boolean firstBlock;
	
	
	/**
	 * Constructor
	 * 
	 * @param ti - torrentInfo object we made from the torrentFile argument
	 * @param fileName - The name we will use for our new file. Given as an argument.
	 * @throws FileNotFoundException
	 */
	public Torrent(TorrentInfo ti, String fileName) throws FileNotFoundException{
		this.torrentInfo = ti;
		this.fos = new FileOutputStream(new File(fileName));
		this.peerID = generateRandomID();
		this.infoHash = encodeInfoHash(torrentInfo.info_hash.array());
		this.downloaded = 0;
		this.uploaded = 0;
		this.left = torrentInfo.file_length;
		this.event = "";
		
		firstBlock = true;
		
		this.requestBuffer = ByteBuffer.allocate(17);
		
		peers = new ArrayList<Peer>();
		
	}
	
	/**
	 * set the tracker URL that will be used to create a connection with the tracker.
	 * Event is left out here because it's the first request we send.
	 * 
	 * @throws MalformedURLException
	 */
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
	
	/**
	 * The URL we use when we start the download.
	 * 
	 * @throws MalformedURLException
	 */
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
	
	/**
	 * The URL we use when we finish the download.
	 * 
	 * @throws MalformedURLException
	 */
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
	
	/**
	 * The URL we use when we are ready to close everything.
	 * 
	 * @throws MalformedURLException
	 */
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
	
	/**
	 * send a request to the tracker to get the list of peers.
	 * The URL is made with the setTrackerURL functions.
	 * 
	 * @throws IOException
	 * @throws BencodingException
	 */
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
	
	/**
	 * iterate through list of peers received from trackers to obtain the ones that have the -RU prefix
	 * 
	 * @throws UnsupportedEncodingException
	 */
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
	
	/**
	 * download from fastest peer. We ping the peers to determine 
	 * the fastest one.
	 * 
	 * @throws IOException
	 * @throws BencodingException
	 * @throws InterruptedException
	 */
	public void start() throws IOException, BencodingException, InterruptedException{
		Peer p = getFastestPeer(peers);
		//Peer p = peers.get(0);
		
		download(p);
	}
	
	/**
	 * Clean up at the end. Send the stopped event to the tracker
	 * and then close up sockets and streams.
	 * 
	 * @throws IOException
	 * @throws BencodingException
	 */
	public void close() throws IOException, BencodingException{
		
		setTrackerURLEventStopped();
		sendRequestToTracker();
		
		input.close();
		output.close();
		socket.close();
	}
	
	/**
	 * Given a list of peers, ping each one 10 times
	 * and return the peer with the lowest RTT average.
	 * 
	 * @param peers - list of peers with -RU prefix
	 * @return the peer with the fastest RTT.
	 */
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
	
	/**
	 * Send a ping to a peer to test its RTT
	 * 
	 * @param peer - the peer we are contacting.
	 * @return the time it takes to get a ping back (in milliseconds).
	 */
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
	
	/**
	 * begin the process of downloading from peer.
	 * Open up streams, perform handshake, send interested,
	 * receive unchoke, and then finally perform the downloading of all 
	 * the pieces.
	 * 
	 * @param peer
	 * @throws IOException
	 * @throws BencodingException
	 * @throws EOFException
	 * @throws InterruptedException
	 */
	public void download(Peer peer) throws IOException, BencodingException, EOFException, InterruptedException{

		String ip = peer.getIP();
		int port = peer.getPort();

		//open up streams and sockets
		
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

		//DISCARD BITFIELD - not needed

		//send interested

		byte[] messages = new byte[6];
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


		//check for unchoke from peer
		
		if(messages[4] == 1){
			System.out.println("Unchoke complete");
			peer.setUnchoked(true);
		}

		trackerResponse.clear();
		
		//Send event started to tracker
		setTrackerURLEventStarted();
		sendRequestToTracker();

		//printTrackerResponse();


		//the array we will use for each piece before we verify its hash and then write the data to the file.
		byte[] pieceArray = new byte[torrentInfo.piece_length];
		ByteBuffer pieceBuffer = ByteBuffer.wrap(pieceArray);
		

		System.out.println(torrentInfo.piece_length);
		
		System.out.println("Starting download...");
		
		long startTime = System.currentTimeMillis();
		
		//this byte array is for each block header, which is 13 bytes.
		byte[] test = new byte[13];
		ByteBuffer testBuffer = ByteBuffer.wrap(test);
		
		//download loop
		while(left > 0){
			
			//clear buffers
			pieceBuffer.clear();
			testBuffer.clear();

			//send request for block
			try {
				sendRequest();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			Thread.sleep(1000);
			
			//read block header
			try {
				if(left >= LENGTH)
					System.out.println(input.read(test));
				else
					input.read(test);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			
			
			
			
			
			
			
			//read data to piece array
			try {
				if(left < LENGTH){
					 input.read(pieceArray, 0, left);
					 fos.write(pieceArray);
					 break;
				}else if(firstBlock)
					 input.read(pieceArray);
				else
					 input.read(pieceArray, LENGTH, LENGTH);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			
			
		
			//increment values
			this.downloaded += LENGTH;
			this.left -= LENGTH;

			System.out.println("Downloaded: " + this.downloaded + "   Index: " + index + "    First block? " + this.firstBlock);
			
			//messagesBuffer.clear();

			//clear buffer for header
			testBuffer.clear();
			
			//alternate between first and second block.
			this.firstBlock = !this.firstBlock;

			if(this.firstBlock){
				
				//check hash
				
				boolean check = (checkArrayEquality(getPieceHash(pieceArray), torrentInfo.piece_hashes[index].array()));
				
				//hash is verified. Write to file and increment piece index.
				if(check){
					System.out.println("Hash verified");
					fos.write(pieceArray);
					index++;
				//Hash failed. Download this piece again.	
				}else{
					System.out.println("Hash failed...reattempting piece download");
					this.downloaded -= LENGTH*2;
					this.left += LENGTH*2;
					Thread.sleep(1000);

					
				}
				
				
						
						
			}
			

		}
		
		//at the end of the download we have the time it took to download.
		long downloadTime = System.currentTimeMillis() - startTime;
		
		System.out.println("Download finished in " + downloadTime + "ms.");
		
		//send completed message to tracker
		setTrackerURLEventCompleted();
		sendRequestToTracker();
		
		



	}
	
	/**
	 * Given a piece array, get its SHA-1 hash to verify it.
	 * 
	 * @param data - the raw bytes for the piece
	 * @return a byte array of size 20 with the SHA-1 hash sequence.
	 */
	public byte[] getPieceHash(byte[] data){
		
		MessageDigest md = null;
		
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return (md.digest(data));
		
	}

	/**
	 * see if two byte arrays are equal.
	 * 
	 * used for checking responses from peer
	 * as well as for verifying a piece's SHA-1 hash.
	 * @param one - one byte array
	 * @param two - another byte array
	 * @return true if the arrays are equal
	 */
	public boolean checkArrayEquality(byte[] one, byte[] two){
		
		if (one.length != two.length){
			return false;
		}
		
		for(int i = 0; i < one.length; i++){
			
			//System.out.println(one[i] + " : " + two[i]);
			
			if(one[i] != two[i]){
				
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * send a handshake message to the peer
	 * 
	 * @throws IOException
	 */
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
	
	/**
	 * send a have message to the peer
	 * 
	 * @throws IOException
	 */
	public void sendHave() throws IOException{
		
		ByteBuffer buffer = ByteBuffer.allocate(9);
		
		buffer.putInt(5);
		buffer.put((byte) 4);
		buffer.putInt(index);
		
		sendMessage(buffer);
		
	}
	
	/**
	 * Send a request message to the peer for a new block.
	 * 
	 * @throws IOException
	 */
	public void sendRequest() throws IOException{
		
		int length; 
		
		if(left < LENGTH){
			length = left;
		}else{
			length = LENGTH;
		}
			

		requestBuffer.put((byte) 0);
		requestBuffer.put((byte) 0);
		requestBuffer.put((byte) 0);
		requestBuffer.put((byte) 13);
		requestBuffer.put((byte) 6);

		requestBuffer.putInt(index);

		if(firstBlock)
			requestBuffer.putInt(0);
		else
			requestBuffer.putInt(LENGTH);
		
		requestBuffer.putInt(length);

		sendMessage(requestBuffer);
		
		requestBuffer.clear();
		
	}
	
	/**
	 * send an unchoke message to the peer.
	 * The peer will be sending this message to us
	 * to let us know that we can download from it.
	 * 
	 * @throws IOException
	 */
	public void sendUnchoke() throws IOException{
		ByteBuffer buffer = UNCHOKE;
		sendMessage(buffer);
	}
	
	/**
	 * send an interested message to the peer. Lets the peer know that we 
	 * want to be unchoked.
	 * 
	 * @throws IOException
	 */
	public void sendInterested() throws IOException{
		ByteBuffer buffer = INTERESTED;
		sendMessage(buffer);
	}
	
	/**
	 * method for sending some message to peer
	 * 
	 * @param message - the bytebuffer that we want to send to the peer
	 * @throws IOException
	 */
	public void sendMessage(ByteBuffer message) throws IOException{
		if(!socket.isConnected()){
			System.out.println("socket not connected");
			return;
		}
		
		output.write(message.array());
	}
	
	
	/**
	 * For debugging. Printing the map that is received from the tracker
	 */
	public void printTrackerResponse(){
		ToolKit.print(trackerResponse);
	}
	
	/**
	 * generate a random 20 byte/char ID to be my peer ID. We need this for
	 * the tracker communication.
	 * 
	 * @return a string of 20 random letters
	 */
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
	
	
	/**
	 * Encodes the info_hash value from the torrent info into the hex 
	 * string that is appended to the URL.
	 * 
	 * @param bytes - the info_hash byte array from the torrent info object.
	 * @return the encoded string that we attach to the tracker URL
	 */
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