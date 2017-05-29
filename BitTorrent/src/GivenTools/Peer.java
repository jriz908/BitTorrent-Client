package GivenTools;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


public class Peer implements Runnable {
	private byte[] peer_id;
	private String peer_ip;
	private int peer_port;
	private Socket peerSocket;
	private DataOutputStream toPeer;
	private DataInputStream fromPeer;
	private int block_length;
	private int blocks_per_piece;
	private static TorrentInfo TI;
	private long elapsedTime;
	private int peerThreadID;
	private static int last_piece_length; 
	
	public Peer (byte[] id, String ip, int port, int threadID) {
		peer_id = id;
		peer_ip = ip;
		peer_port = port;
		peerThreadID = threadID;
		TI = RUBTClient.getTorrentInfo();
		block_length = RUBTClient.block_length;
		blocks_per_piece = TI.piece_length / block_length;
		last_piece_length = TI.file_length - ((TI.piece_hashes.length-1) * blocks_per_piece * block_length ); //calculates the length of the last piece
		elapsedTime = 0;
	}
	
	
	public void run() {
		
		//stalls until user starts download
		while (!RUBTClient.getStart()) {
			
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				
			}
		} 
		
		if (isDownloadPeer()) {
			tryHandshakeAndDownload(RUBTClient.info_hash, RUBTClient.getGeneratedPeerID(), RUBTClient.getPiecesHash());
		} else {
			handleIncomingPeer();
		}
	}
	
	public void tryHandshakeAndDownload(byte[] info_hash, String generatedPeerID, ByteBuffer[] piece_hashes) {
    	byte[] peersHandshake = new byte[68]; //28 + 20 + 20 ; fixedHeader, info_Hash, peerID
    	
	    try {
	    	peerSocket = new Socket(peer_ip, peer_port);
	    	toPeer = new DataOutputStream(peerSocket.getOutputStream());
			fromPeer = new DataInputStream(peerSocket.getInputStream());
			byte[] handshakeHeader = createHandshakeHeader(info_hash, generatedPeerID);

			//Perform handshake
			toPeer.write(handshakeHeader);
			fromPeer.readFully(peersHandshake, 0, peersHandshake.length); //read fromPeer and store 68 bytes into peersHandshake
			
			//System.out.println("What I sent.........: " + Arrays.toString(handshakeHeader));
			//System.out.println("Response from server: " + Arrays.toString(peersHandshake));
			//Check if peersHandshake contains the same info_hash as the one inside the tracker AND it has the same peerID has the peerID stored inside this instance of Peer!
			//Extract info_hash and peerID out of the peersHandshake!
			//And call isEqualByteArray(info_hash, peersHandshake.info_hash) and isEqualByteArray(peer_id, peersHandshake.peerID)
			
			if (!checkHandshakeResponse(info_hash, peersHandshake)) {
				System.err.println("Peer responded with an invalid handshake.");
				closeResources();
				return;
			} 
			
			/**
			 * The peer should immediately respond with his own handshake message, which takes the same form as yours. Otherwise drop connection.
			 */
			
			//Download file?
			PeerMessages p = new PeerMessages();
			boolean result;
			
			p.start(this);
			result = p.showInterest();
			
			//unchoked and interested, can download file
			if (result) {
				 
				long two_minutes = System.nanoTime();
				long keep_alive = 0;
				long started = System.nanoTime();
				
				/*Thread the peer? download from 3 peers
				 *	Peer implements Runnable
				 *	add a run method to Peer...  run calls tryhandshakeanddownload
				 * 		When downloading pieces, increment piece index by 3, because we will have 3 peers.
				 * 		How to save all these pieces in a buffer?
				 * 		when requested by a peer, How to upload pieces to peers?
				 */
				
				//piece_hashes.length - number of pieces to download
				//Incrementing by PeerListLength because we have multiple threads downloading pieces.
				//For example
					//thread0 downloads every n piece starting from 0
					//thread1 downloads every n piece starting from 1
					//thread2 downloads every n piece starting from 2
				int incr = RUBTClient.getDownloadPeers().size();
				
				for (int i = getPeerThreadID() + RUBTClient.getTXTNUM() + 1; i < piece_hashes.length; i = i + incr) {
					
					//System.out.println("Requesting piece index: " + (i+1));
					ByteArrayOutputStream piece = new ByteArrayOutputStream();
					int x = 0;
					
					keep_alive = System.nanoTime() - two_minutes;
					
					if (NANOSECONDS.toMinutes(keep_alive) >= 2) {
						p.keepAlive();
						two_minutes = System.nanoTime();
					}
						
					if (i+1 == piece_hashes.length) {
						
						int temp = last_piece_length;
						byte[] resultingPiece;
						while (temp > 0) {
							
							if (temp > block_length) {
								p.request(i, x, block_length);
								resultingPiece = p.getPiece(block_length);
							} else {
								p.request(i, x, temp);
								resultingPiece= p.getPiece(temp);
							}
							
							temp -= block_length;
		 					x+= block_length;
		 					
							piece.write(resultingPiece);	
						}
						
					} else {
						// gets all the blocks that make up a given piece
						for (int j = 0; j < blocks_per_piece; j++) {
		 					p.request(i, x, block_length);
		 					byte[] resultingPiece = p.getPiece(block_length);
		 					
		 					x+= block_length;
							piece.write(resultingPiece);	
						}
					}
					/** 
					 *  has an SHA1 hash for each piece of the file and the pieces are verified as the finish downloading, 
					 *  and are discarded if they fail to match the hash, indicating something wrong was transmitted to you.
					 */
					byte[] SHA1digest = digestToSHA1(piece.toByteArray());
					if (isEqualSHA1(piece_hashes[i].array(), SHA1digest)) {
						System.out.println("Piece " + (i+1) +" verified by threadID: " + peerThreadID);
						
						if ( i+1 == piece_hashes.length ) {
							RUBTClient.setProgress(piece_hashes.length);
						} else {
							RUBTClient.setProgress(RUBTClient.getProgress() + 1);
						}
						
						/**
						 * If you wish to serve files as well as download them, 
						 * you should send a Have message for the piece to all connected peers
						 * once you have the full and hash-checked piece.
						 */
						p.sendHave(i);
						
						if (RUBTClient.getStop()) {
							elapsedTime = System.nanoTime() - started;
							
							if ( i < RUBTClient.getTXTNUM() || RUBTClient.getTXTNUM() == -1)
								RUBTClient.setTXTNUM(i);
							else if (i - 3 > RUBTClient.getTXTNUM()) {
								RUBTClient.setTXTNUM(i);
							}
							
							System.out.println("Interrupt received by ThreadID: " + getPeerThreadID() );
							closeResources();
							Thread.currentThread().interrupt();
							return;
						}
						
					} else {
						System.out.println("Piece " + (i+1) +" IS NOT verified" +" verified by threadID: " + peerThreadID);
						
						//invalid piece need to re-send request for that piece
						i--;
						continue;
					}
					
					//TODO Store piece in a buffer, upload pieces to peers if requested, if we have the piece?
					putPieceIntoDownloadedBuffer(i, piece.toByteArray());
				}
				
				elapsedTime = System.nanoTime() - started;
			}
			
			closeResources();
	    }
	    catch (IOException e) {
	    	System.err.println("Could not perform handshake and download file: " + e);
	    }
	    
	    return;
	}
	
	public void handleIncomingPeer() {
		byte[] incomingPeersHandshake = new byte[68]; //28 + 20 + 20 ; fixedHeader, info_Hash, peerID
		
		//Set peer socket in incomingpeer
		try {
			toPeer = new DataOutputStream(peerSocket.getOutputStream());
			fromPeer = new DataInputStream(peerSocket.getInputStream());
			fromPeer.readFully(incomingPeersHandshake, 0, incomingPeersHandshake.length); //read fromPeer and store 68 bytes into peersHandshake
	//		fromPeer.read(incomingPeersHandshake);
		} catch (IOException e1) {
			System.err.println("Failed to read handshake from incoming peer" + e1);
		}
		System.out.println("From incoming peer: " + Arrays.toString(incomingPeersHandshake));
		
		/**
		 * When serving files, you should check the incoming peers handshake to verify that the info_hash
		 * matches one that you are serving and close the connection if not.
		 */
		if (!checkIncomingInfoHash(incomingPeersHandshake)) {
			System.err.println("Incoming peer sent an invalid info hash.");
			closeResources();
			return;
		}
		
		try {
			toPeer.write(createHandshakeHeader(RUBTClient.info_hash, RUBTClient.getGeneratedPeerID()));
		} catch (IOException e1) {
			System.err.println("Failed to write handshake to incoming peer" + e1);
		}
		
		PeerMessages p = new PeerMessages();
		p.start(this);
		
		//SEND BITFIELD MESSAGE
		p.sendBitfield();
		
		//LISTEN FOR REQUESTS, SEND PIECES(sendPiece is implemented in PeerMessages.java)
		//while (keepalive?) {
		
		int stopCount = 0;
		
		while (!p.peerInterest()) {
			try {
				Thread.sleep(1000);
				if (stopCount > 30) {
					System.err.println("Did not receive interested message after 30. Closing resources from uninterested incoming peer.");
					closeResources();
					return;
				}
				stopCount++;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Past interest");
		
		p.unchoke();
		
		long start = System.nanoTime();
		long timePassed = 0;
		
		byte[] len = new byte [4];
		
		while (true) {
			if ( NANOSECONDS.toMinutes(((timePassed = System.nanoTime()) - start)) > 2) {
				p.choke();
				break;
			}
			
			try {
				fromPeer.readFully(len, 0, len.length);
			} catch (Exception e) {
				break;
			}
			
			if (Arrays.equals(len, p.getKeepAlive())) {
				start = System.nanoTime();
			} else if (Arrays.equals(len, p.getRequestLength())) {
				try {
					int x = fromPeer.readByte();
					
					if (x != 6 ) {
						p.choke();
						break;
					}
				} catch (IOException e) {
					System.err.println("Failed to read byte from incoming peer: " + e);
				}
				
				
				try {
					int index = fromPeer.readInt();
				
					int begin = fromPeer.readInt();
					int length = fromPeer.readInt();
					
					byte [] piece = RUBTClient.getDownloadedPieces()[index];
					byte [] block = Arrays.copyOfRange(piece, begin, begin + length);
					
					byte [] have = p.getHavePrefix();;
					byte [] id;
					
					int count  = 0;
					do {
						p.sendPiece(index, begin, length, block);
						id = new byte [4];
						fromPeer.readFully(id);
						Thread.sleep(1000);
						count++;
						if (count == 10) {
							p.choke();
							return;
						}
					} while (!Arrays.equals(have, id));
					
					fromPeer.readFully(new byte[5]);
					
					if (RUBTClient.getStop() == true) {
						p.choke();
						Thread.currentThread().interrupt();
						return;
					}
					
				} catch (IOException e) {
					System.err.println("Failed to read request from incoming peer and send piece: " + e);
				} catch (InterruptedException e) {
					System.err.println("Failed to sleep" + e);
					e.printStackTrace();
				}
				
			} else {
				p.choke();
				break;
			}
		}
		
		//}
	}
	
	private void putPieceIntoDownloadedBuffer(int pieceIndex, byte[] byteArray) {
		RUBTClient.getDownloadedPieces()[pieceIndex] = byteArray;
	}

	private byte[] digestToSHA1(byte[] buffer) {
		
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("Failed to convert bytes to SHA-1: " + e);
		}
		
		md.update(buffer);
		byte[] digest = md.digest(); //SHA-1 bytes
		
		return digest;
	}
	
	//digestToSHA1 then compare with piece_hash...
	private boolean isEqualSHA1(byte[] piece_hash, byte[] downloaded_hash) {
		if (MessageDigest.isEqual(piece_hash, downloaded_hash)) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Handshaking between peers begins with byte nineteen followed by the string 'BitTorrent protocol'.
	 ** After the fixed headers are 8 reserved bytes which are set to 0. 
	 * Next is the 20-byte SHA-1 hash of the bencoded form of the info value from the metainfo (.torrent) file.
	 * The next 20-bytes are the peer id generated by the client. 
	 * The info_hash should be the same as sent to the tracker, and the peer_id is the same as sent to the tracker. 
	 * If the info_hash is different between two peers, then the connection is dropped.
	 **/
	private byte[] createHandshakeHeader(byte[] info_hash, String generatedPeerID) {
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		byte[] fixedHeader = {19, 'B','i','t','T','o','r','r','e','n','t',' ', 'p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0};
		
		try {
			header.write(fixedHeader);
			header.write(info_hash);
			header.write(generatedPeerID.getBytes());
		} catch (IOException e) {
			System.err.println("Failed to generate handshake header.");
		}
		
		return header.toByteArray();
	}
	
	public boolean checkHandshakeResponse(byte[] info_hash, byte[] peersHandshake) {
		byte[] fixedHeader = {19, 'B','i','t','T','o','r','r','e','n','t',' ', 'p','r','o','t','o','c','o','l',0,0,0,0,0,0,0,0}; //Used for checking
		byte[] peersHeader = new byte[28];
		byte[] peersInfoHash = new byte[20];
		byte[] peersID = new byte[20];
		
		//From peersHandshake starting at index 0, copy 28 bytes into peersHeader starting at index 0
		System.arraycopy(peersHandshake, 0, peersHeader, 0, 28); 
		//Check if valid fixed header
		if (!isEqualByteArray(fixedHeader, peersHeader)) {
			System.out.println("The header is wrong");
			return false;
		}
		
		//From peersHandshake starting at index 28, copy 20 bytes into peersInfo starting at index 0
		System.arraycopy(peersHandshake, 28, peersInfoHash, 0, 20);
		if (!isEqualByteArray(info_hash, peersInfoHash)) {
			System.out.println("The info hash is wrong");
			return false;
		}
		
		//From peersHandshake starting at index 48, copy 20 bytes into peersID starting at index 0
		System.arraycopy(peersHandshake, 48, peersID, 0, 20);
		if (!isEqualByteArray(peer_id, peersID)) {
			return false;
		}
		
		return true;
	}
	
	private boolean checkIncomingInfoHash(byte[] incomingHandshake) {
		byte[] incomingInfoHash = new byte[20];
		
		//From peersHandshake starting at index 28, copy 20 bytes into peersInfo starting at index 0
		System.arraycopy(incomingHandshake, 28, incomingInfoHash, 0, 20);
		if (!isEqualByteArray(RUBTClient.info_hash, incomingInfoHash)) {
			System.out.println("The info hash is wrong");
			return false;
		}
		return true;
	}
	
	//Use Arrays.equals() if you want to compare the actual content of arrays that contain primitive types values (like byte).
	//Checks if two byte arrays are equal
	public boolean isEqualByteArray(byte[] b1, byte[] b2) {
		if (Arrays.equals(b1, b2)) {
			return true;
		} else {
			return false;
		}
	}
	
	//Unused leftover from PhaseII
	//Writes bytes to a filepath. Will be used to write downloaded file into provided file path at args[1]
	@SuppressWarnings("unused")
	private static void writeToFile(byte[] bytes) {
			
		try {
		    RUBTClient.getStream().write(bytes);
    	} catch (IOException e) {
    		System.err.println("Writing to filestream failed: " + e);
		}
	}
	
	public boolean isDownloadPeer() {
		if (this.getPeerThreadID() > 99) {
			return false;
		} else {
			return true;
		}
	}
	
	private void closeResources() {
		try {
			toPeer.close();
			fromPeer.close();
			peerSocket.close();
		} catch (IOException e) {
			System.err.println("Closing resources failed: " + e);
		}
	}
	
	public void printPeer() {
		System.out.println("---"); 
		System.out.println("peerID: " + getPeerID());
		System.out.println("peerIP: " + getPeerIP());
		System.out.println("peerPort: " + getPeerPort());
		System.out.println("peerThreadID: " + getPeerThreadID());
	}
	
	public String getPeerID() {
		try {
			return (new String(peer_id,"ASCII"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public String getPeerIP() {
		return peer_ip;
	}
	
	public int getPeerPort() {
		return peer_port;
	}

	public Socket getSocket() {
		return peerSocket;
	}
	
	public DataOutputStream getOutput() {
		return toPeer;
	}
	
	public DataInputStream getInput() {
		return fromPeer;
	}
	
	public long getElapsedTime() {
		return elapsedTime;
	}
	
	public int getPeerThreadID() {
		return peerThreadID;
	}
	
	public void setPeerSocket(Socket peerSocket) {
		this.peerSocket = peerSocket;
	}

	public void setToPeer(DataOutputStream toPeer) {
		this.toPeer = toPeer;
	}

	public void setFromPeer(DataInputStream fromPeer) {
		this.fromPeer = fromPeer;
	}
	
}
