/**
 * 
 */
package GivenTools;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
/**
 * @author Min Chai 
 * @author Terence Williams
 *
 */
public class RUBTClient{

	/**
	 * @param args
	 */

	/**
	 * Key used to retrieve the interval from the tracker 
	 */
	//public final static ByteBuffer KEY_INTERVAL = 
	//ByteBuffer.wrap(new byte[]{ 'i', 'n', 't', 'e','r','v','a','l' });
	/**
	 * Key used to retrieve the peer list from the tracker 
	 */
	public final static ByteBuffer KEY_PEERS = 
			ByteBuffer.wrap(new byte[]{ 'p', 'e', 'e', 'r','s'});
	/**
	 * Key used to retrieve the peer id from the tracker 
	 */
	public final static ByteBuffer KEY_PEER_ID = 
			ByteBuffer.wrap(new byte[]{ 'p', 'e', 'e', 'r',' ','i','d'});
	/**
	 * Key used to retrieve the peer port from the tracker 
	 */
	public final static ByteBuffer KEY_PEER_PORT = 
			ByteBuffer.wrap(new byte[]{ 'p', 'o', 'r', 't'});
	/**
	 * Key used to retrieve the peer ip from the tracker 
	 */
	public final static ByteBuffer KEY_PEER_IP = 
			ByteBuffer.wrap(new byte[]{ 'i', 'p'});

	//block length set to 2 ^ 14
	public static final int block_length = 16384;

	//private static int interval;
	private static List<Peer> peers;

	//Peers which we will download from
	private static List<Peer> downloadPeers;

	//The info_hash should be the same as sent to the tracker, and the peer_id is the same as sent to the tracker.
	//If the info_hash is different between two peers, then the connection is dropped.
	public static byte[] info_hash = null;
	private static String generatedPeerID = null;
	private static ByteBuffer[] piece_hashes = null; //The SHA-1 hash of each piece!
	private static byte[][] downloadedPieces = null;  //Buffer to store downloadedPieces
	private static TorrentInfo TI;
	private static FileOutputStream file_stream;
	private static int downloadthreadID = 0; //Used to give each Peer a threadID
	private static int threadID = 100; ////Used to give each Peer a threadID
	private static int portno = -1;
	private static int progress;
	private static int TXTNUM;
	private static boolean stop;
	private static boolean start;
	private static long downloadTime;

	/*
	TODO
		read TXTNUM from txtfile, if nothing leave as -1
		otherwise TXTNUM = number from txtfile
	 */

	public static void main(String[] args) throws InterruptedException {
		URL url = null;

		HashMap tracker_info = null;
		TI = null;
		stop = false;
		start = false;
		
		//		TI = openTorrent("src/GivenTools/CS352_Exam_Solutions.mp4.torrent");
		TI = openTorrent(args[0]);
		url = getURL(TI);

		//Get the host and portno by using the TorrentInfo
		//		hostName = url.getHost();
		portno = 6881;

		piece_hashes = TI.piece_hashes;
		
		if ( (new File ("downloaded")).exists() ) {
			
			try {
				FileInputStream fs = new FileInputStream ("time");
				DataInputStream ds = new DataInputStream (fs);
				downloadTime = ds.readLong();
				ds.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			try {
			ObjectInputStream ois = new ObjectInputStream (new FileInputStream ("downloaded"));
			downloadedPieces = (byte[][]) ois.readObject();
			ois.close();
			
				for(int i = 0; i < downloadedPieces.length; i++) {
					if (downloadedPieces[i] == null) {
						TXTNUM = i - 1;
						progress = TXTNUM;
						break;
					}
				}
			
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			TXTNUM = -1;
			progress = 0;
			downloadTime = 0;
			
			//Initialize buffer size to number of pieces expected to download
			downloadedPieces = new byte[piece_hashes.length][];
		}

		//connects to the tracker and retrieves the interval and peer list data
		tracker_info = connectTracker(TI, url, portno);

		//interval = ((Integer)tracker_info.get(KEY_INTERVAL)).intValue();
		buildPeerList(tracker_info);
		buildDownloadPeerList();

		//prints out the info decoded from the tracker
		ToolKit.print(tracker_info);

		System.out.println("My generatedPeerID: " + generatedPeerID);
		System.out.println();

		//The first time you begin the download,
		//you need to contact the tracker and let it know you are starting to download.
		try {
			contactTrackerWithStartedEvent();
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}
		System.out.println("------------ \nPress Start to start the download. You may press Quit the stop the download and resume at a later time.\n");

		System.out.println("Setting up threads\n");
		
//		IncomingPeer listenIncomingPeer = new IncomingPeer();
//		Thread t = new Thread(listenIncomingPeer);
//		t.start(); //calls run method in IncomingPeer class

		//Make a list of downloading threads to join(to block the main method)
		List<Thread> downloading_threads = new ArrayList<Thread>();
		
		UserInput input = new UserInput();
		Thread user = new Thread (input);
		downloading_threads.add(user);
		user.start();
		
		System.out.println("downloadPeer list size: " + getDownloadPeers().size());

		for (Peer peer : downloadPeers) {
			Thread downloading = new Thread(peer);
			downloading_threads.add(downloading);
			downloading.start(); //Calls run method in Peer class
		}
		
		// Allow downloading threads to finish before continuing the main method
		for (Thread downloading : downloading_threads) {
			downloading.join();
		}
		
		if (progress < TI.piece_hashes.length) {
			downloadTime += downloadPeers.get(0).getElapsedTime();
			
			try {
				FileOutputStream fs = new FileOutputStream ("time");
				DataOutputStream ds = new DataOutputStream (fs);
				ds.writeLong(downloadTime);
				ds.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			try {
				ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream ("downloaded"));
				oos.writeObject(downloadedPieces);
				oos.close();
				System.exit(0);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Writing downloaded buffer to stream...");
		
		//String path = "src/GivenTools/newfile.mov";
		createFileStream(args[1]);
		
		//Write all pieces to file
		for (byte[] piece : downloadedPieces) {
			if (piece != null && piece.length > 0 )
				try {
					file_stream.write(piece);
				} catch (IOException e) {
					System.err.println("Failed to write piece to stream: " + e);
				}
		}
		
		/**
		 * delete the files that help with data persistence
		 * once the download has been completed
 		 * downloaded holds the byte array which stores the pieces of the file
 		 * time holds the time it takes to complete the full download
 		 * 
		 */
		if ( (new File ("downloaded")).exists() ) {
			try{
				File f = new File ("downloaded");
				f.delete();
				
				File f2 = new File ("time");
				f2.delete();
			} catch (Exception e) {
				System.err.println("Cannot delete file");
			}
		}
		
		System.out.println("Finished writing buffer to stream.");
		System.out.println("Contacting tracker with completed event...");
		//When the file is finished, you must contact the tracker and send it the completed event and properly close all TCP connections
		try {
			contactTrackerWithCompletedEvent();
			File f = new File ("data");
			f.delete();
		} catch (MalformedURLException e) {
			System.err.println("Could not contact tracker with completed event.");
		}

		System.out.println("Contacting tracker with stopped event...");
		//Before the client exits it should send the tracker the stop event
		try {
			contactTrackerWithStoppedEvent();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		downloadTime += downloadPeers.get(0).getElapsedTime();

		System.out.println("----------------------------------------------");
		System.out.println("Total download time: " + NANOSECONDS.toMinutes(downloadTime) + " mins");

		try {
			file_stream.close();
		} catch (IOException e) {
			System.err.println("Failed to close file_stream: " + e);
		}
		
	}

	//Gets TorrentInfo from torrent file
	private static TorrentInfo openTorrent(String args0) {
		byte[] bytes = null;
		File file = null;
		TorrentInfo TI = null;

		try {
			file = new File(args0);
			DataInputStream dis = new DataInputStream(new FileInputStream(file));
			bytes = new byte[(int) file.length()];
			dis.readFully(bytes);
			dis.close();
		} catch (final FileNotFoundException e) {
			System.err.println("The torrent file was not found: " + e);
		} catch (final IOException e) {
			System.err.println("Failed to open torrent file: " + e);
		}

		try {
			TI = new TorrentInfo(bytes);
		} catch (BencodingException e) {
			System.err.println("Failed to get TorrentInfo: " + e);
		}
		return TI;
	}

	
	
	public static void createFileStream (String path) {
		File file = new File (path);

		try {
			file_stream = new FileOutputStream(file); //true allows append
		} catch (FileNotFoundException e) {
			System.err.println("Failed to create a fileoutputstream: " + e);
		}

	}

	private static HashMap connectTracker(TorrentInfo TI, URL url, int portno) {
		URL tracker = null;
		String getRequest = null;
		String peerID = null;
		HttpURLConnection tracker_connect = null;
		HashMap decode = null;
		String hash = null;

		//creating tracker connection url
		try {
			peerID = generatePeerID();
			generatedPeerID = peerID;
			hash = URLEncoder.encode(new String(TI.info_hash.array(), "ISO-8859-1"),"ISO-8859-1");
			info_hash = TI.info_hash.array();

			getRequest = url +
					String.format("?info_hash=%s&peer_id=%S&port=%s&uploaded=0&downloaded=0&left=%s", 
							hash,peerID,portno,TI.file_length);
			tracker = new URL(getRequest);
		} catch (Exception e) {
			System.err.println("Failed to create getRequest in connectTracker: " + e);
		}

		//Making the connection with the tracker 
		try {
			tracker_connect = (HttpURLConnection)tracker.openConnection();
			tracker_connect.setConnectTimeout(5000);
		} catch (Exception e) { 
			System.err.println("Failed to connect to tracker: " + e);
		}

		//try reading info from tracker
		try {
			BufferedInputStream tracker_response = new BufferedInputStream(tracker_connect.getInputStream());
			ByteArrayOutputStream write_bytes = new ByteArrayOutputStream(); //write_bytes just holds the bytes; not being sent
			byte[] bytes = new byte[1];
			
			while(tracker_response.read(bytes) != -1)
				write_bytes.write(bytes);

			byte[] response = write_bytes.toByteArray();

			//ToolKit.print(response);//info before it's decoded
			decode = (HashMap)Bencoder2.decode(response);	
		} catch (Exception e) {
			tracker_connect.disconnect();
			System.err.println("Failed to read bytes from tracker: " + e);
		}

		tracker_connect.disconnect();
		return decode;
	}

	private static void buildPeerList(HashMap info){
		ArrayList list = (ArrayList)info.get(KEY_PEERS);
		peers = new ArrayList<Peer>();
		CharSequence cs= "-RU";

		for (int i = 0; i < list.size(); i++) {
			HashMap peer_info = (HashMap)list.get(i);
			String id = null;
			//gets peer id, ip and port
			byte[] peer_id = ((ByteBuffer)peer_info.get(KEY_PEER_ID)).array();
			String ip = new String(((ByteBuffer)peer_info.get(KEY_PEER_IP)).array());
			int port = ((Integer)peer_info.get(KEY_PEER_PORT)).intValue();

			try {
				id = (new String(peer_id,"ASCII"));

				//creates new peer and adds it to the peer list
				// use only the peers with peer_id prefix -RU
				if (id.contains(cs)) {
					Peer p = new Peer(peer_id, ip, port, downloadthreadID);
					peers.add(p);
					downloadthreadID++; //Increment for a new unused downloadthreadID
				} else {
					Peer p = new Peer(peer_id, ip, port, threadID);
					peers.add(p);
					threadID++; //Increment for a new unused threadID
				}
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
	}

	private static void buildDownloadPeerList() {
		//Download only from peers whose ID begins with RU
		//This is a list of peers that have all the pieces downloaded
		downloadPeers = new ArrayList<Peer>();
		for (Peer peer : peers) {
			if (peer.getPeerID().contains("-RU") && 
					(peer.getPeerIP().equals("172.16.97.11") || peer.getPeerIP().equals("172.16.97.12") || peer.getPeerIP().equals("172.16.97.13"))) {
				downloadPeers.add(peer);
			}	
		}
	}

	//Generates a random peerId with length 20
	private static String generatePeerID() {
		String s = "0123456789ABCDEFGHIJKLMNOPQSTVWXYZ";
		Random r = new Random();
		String peerID = "";

		for (int i = 0; i < 20; i++)
			peerID += s.charAt(r.nextInt(s.length()));

//		peerID = "11111222223333344444";
		return peerID;
	}

	//you need to contact the tracker and let it know you are STARTING the download
	private static void contactTrackerWithStartedEvent() throws MalformedURLException {
		URL url = TI.announce_url; 
		int portno = url.getPort();
		URL tracker = null;
		String getRequest = null;
		HttpURLConnection tracker_connect = null;
		String hash = null;

		try {
			hash = URLEncoder.encode(new String(TI.info_hash.array(), "ISO-8859-1"),"ISO-8859-1");
			getRequest = url +
					String.format("?info_hash=%s&peer_id=%S&port=%s&uploaded=0&downloaded=0&left=%s&event=started", //**"started"**
							hash, RUBTClient.getGeneratedPeerID(), portno, TI.file_length);
			tracker = new URL(getRequest);
		} catch (UnsupportedEncodingException e) {
			System.err.println("Could not contact tracker with started event: " + e);
		}

		try {
			tracker_connect = (HttpURLConnection)tracker.openConnection();
		} catch (IOException e) {
			System.err.println("Could not contact tracker with started event: " + e);
		}
		return;
	}

	//you need to contact the tracker and let it know you are completing the download
	private static void contactTrackerWithCompletedEvent() throws MalformedURLException {
		URL url = TI.announce_url; 
		int portno = url.getPort();
		URL tracker = null;
		String getRequest = null;
		HttpURLConnection tracker_connect = null;
		String hash = null;

		try {
			hash = URLEncoder.encode(new String(TI.info_hash.array(), "ISO-8859-1"),"ISO-8859-1");
			getRequest = url +
					String.format("?info_hash=%s&peer_id=%S&port=%s&uploaded=0&downloaded=%s&left=0&event=completed", //**"completed"**
							hash, RUBTClient.getGeneratedPeerID(), portno, TI.file_length);
			tracker = new URL(getRequest);
		} catch (UnsupportedEncodingException e) {
			System.err.println("Failed to contact tracker with completed event: " + e);
		}

		try {
			tracker_connect = (HttpURLConnection)tracker.openConnection();
			//System.out.println("Contacted tracker to verify download was successful");
			tracker_connect.disconnect();
		} catch (IOException e) {
			System.err.println("Failed to contact tracker with completed event: " + e);
		}
		return;
	}

	private static void contactTrackerWithStoppedEvent() throws MalformedURLException {
		URL url = TI.announce_url; 
		int portno = url.getPort();
		URL tracker = null;
		String getRequest = null;
		HttpURLConnection tracker_connect = null;
		String hash = null;

		try {
			hash = URLEncoder.encode(new String(TI.info_hash.array(), "ISO-8859-1"),"ISO-8859-1");
			getRequest = url +
					String.format("?info_hash=%s&peer_id=%S&port=%s&uploaded=0&downloaded=%s&left=0&event=stopped", //**"stopped"**
							hash, RUBTClient.getGeneratedPeerID(), portno, TI.file_length);
			tracker = new URL(getRequest);
		} catch (UnsupportedEncodingException e) {
			System.err.println("Failed to contact tracker with stopped event: " + e);
		}

		try {
			tracker_connect = (HttpURLConnection)tracker.openConnection();
			//System.out.println("Contact tracker that client connection is closing");
			tracker_connect.disconnect();
		} catch (IOException e) {
			System.err.println("Failed to contact tracker with stopped event: " + e);
		}
		return;
	}
	
	private static URL getURL(TorrentInfo TI) {
		URL	url = TI.announce_url;
		return url;
	}

	public static byte[] getInfo_hash() {
		return info_hash;
	}

	public static String getGeneratedPeerID() {
		return generatedPeerID;
	}

	public static TorrentInfo getTorrentInfo () {
		return TI;
	}

	public static ByteBuffer[] getPiecesHash () {
		return piece_hashes;
	}
	
	public static FileOutputStream getStream () {
		return file_stream;
	}
	
	public static List<Peer> getDownloadPeers() {
		return downloadPeers;
	}

	public static byte[][] getDownloadedPieces () {
		return downloadedPieces;
	}
	
	public static int getThreadID() {
		return threadID;
	}

	public static void setThreadID(int threadID) {
		RUBTClient.threadID = threadID;
	}
	
	public static int getTXTNUM() {
		return TXTNUM;
	}

	public static void setTXTNUM(int tXTNUM) {
		TXTNUM = tXTNUM;
	}
	
	public static int getProgress () {
		return progress;
	}
	
	public static void setProgress (int p) {
		progress = p;
	}

	public static boolean getStop () {
		return stop;
	}
	
	public static void setStop (boolean s) {
		stop = s;
	}
	
	public static boolean getStart () {
		return start;
	}
	
	public static void setStart (boolean s) {
		start = s;
	}

}
