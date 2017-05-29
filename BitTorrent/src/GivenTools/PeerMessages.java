package GivenTools;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
/**
 * @author Min Chai
 * @author Terence Williams
 *
 */
public class PeerMessages {
	
	private boolean choking;
	private boolean interested;
	private boolean peer_interested;
	private boolean peer_choking;
	private DataOutputStream toPeer;
	private DataInputStream fromPeer;
	private ByteArrayOutputStream out;
	
	private static final byte[] keep_alive = {0,0,0,0};
	
	private static final byte[] length_prefix = {0,0,0,1};
	
	private static final byte[] have_prefix = {0,0,0,5};
	
	private static final byte[] request_length = {0,0,0,13};
	
	private Peer peer;
	
	/**
	 * Key for choke message
	 */
	private static final int KEY_CHOKE = 0;
	
	/**
	 * Key for unchoke message
	 */
	private static final int KEY_UNCHOKE = 1;
	
	/**
	 * Key for interested message
	 */
	private static final int KEY_INTERESTED = 2;
	
	/**
	 * Key for uninterested message
	 */
	private static final int KEY_UNINTERESTED = 3;
	
	/**
	 * Key for have message, verifies piece downloaded
	 */
	private static final int KEY_HAVE = 4;
	
	/**
	 * Key for request message
	 */
	private static final int KEY_REQUEST = 6;
	
	private static final int KEY_PIECE = 7;
	
	public void start(Peer p) {
		choking = true;
		interested = false;
		peer_choking = true;
		peer_interested = false;
		out = new ByteArrayOutputStream();
		toPeer = p.getOutput();
		fromPeer = p.getInput();
		
		peer = p;
		
		if (p.getPeerThreadID() < 99)
			readBitfield();
	}
	
	
    //have: <length prefix> is 5 and message ID is 4. The payload is a zero-based index of the piece that has just been downloaded and verified.
	public void sendHave(int index) {
		try {
			out.reset();
			out.write(have_prefix);
			out.write(KEY_HAVE);
			out.write(ByteBuffer.allocate(4).putInt(index).array()); 
			
			toPeer.write(out.toByteArray());
			//System.out.println("sendHaving for piece: " + (index+1));
		} catch (Exception e) {
			System.err.println("Have message failed: " + e);
		}
	}
	
	public void request(int index, int begin, int length) {
		
		try {
			out.reset();
			out.write(request_length);
			out.write(KEY_REQUEST);
			
			out.write(ByteBuffer.allocate(4).putInt(index).array());
			out.write(ByteBuffer.allocate(4).putInt(begin).array());
			out.write(ByteBuffer.allocate(4).putInt(length).array());
			
			toPeer.write(out.toByteArray());
			//System.out.println("toPeer Request in PeerMessages: " + Arrays.toString(out.toByteArray()));
		} catch (Exception e) {
			System.err.println("Request message failed: " + e);
		}
	}
	
	public byte[] getPiece(int block_length) throws IOException {
		byte[] data = new byte [block_length + 13];
		byte[] block = null;
		
		
		fromPeer.readFully(data);

		/**
		 * piece: <len=0009+X><id=7> <index><begin><block>
		 * The piece message is variable length, where X is the length of the block. The payload contains the following information:
		
		 * index: integer specifying the zero-based piece index
		 * begin: integer specifying the zero-based byte offset within the piece
		 
		 * block: block of data, which is a subset of the piece specified by index. SHOULD BE 16384
		 * 
		 * A Piece message consists of the 4-byte length prefix,
		 *  1-byte message ID,
		 * and a payload with a 4-byte piece index, 4-byte block offset within the piece in bytes
		 * (so far the same as for the Request message), and a variable length block containing the raw bytes for the requested piece.
		 */
		
		try {
			int expected_len = getBytesAsInt(data, 0) - 9; //9 + X, Offset 0
			//System.out.println("Expected len of block: " + expected_len);
			//System.out.println(Arrays.toString(data));
			
			int index = getBytesAsInt(data, 5); //Offset 5
			//System.out.println("Received piece Index: " + index);
			block = new byte[expected_len];

			//From data starting at index 13, copy length bytes into block starting at index 0, return this block		
			System.arraycopy(data, 13, block, 0, expected_len);
			
		} catch (Exception e) {
			System.err.println("Failed to get piece: " + e);
		}
		
		return block;
	}
	
	public void sendPiece(int index, int begin, int block_length, byte[] block) {
		byte[] piece_length = ByteBuffer.allocate(4).putInt(9 + block_length).array();
		
		try {
			System.out.println("piece len: " + Arrays.toString(piece_length));
			out.reset();
			out.write(piece_length);
			out.write(KEY_PIECE);
			
			out.write(ByteBuffer.allocate(4).putInt(index).array());
			out.write(ByteBuffer.allocate(4).putInt(begin).array());
			out.write(block);
			
			System.out.println("Sending piece " + index);
			
			toPeer.write(out.toByteArray());
			} catch (Exception e) {
			System.err.println("Send piece message failed : " + e);
		}
	}
	
	//starting at index x, copy 4 bytes into buffer from data and get the integer represented by these 4 bytes
	private int getBytesAsInt(byte[] data, int x) {
		int result = -1;
		byte[] buffer = new byte[4];
		
		buffer[0] = data[x];
		buffer[1] = data[x+1];
		buffer[2] = data[x+2];
		buffer[3] = data[x+3];
		ByteBuffer bb = ByteBuffer.wrap(buffer);
		result = bb.getInt();
		
		return result;
	}
	
	public void keepAlive () {
		
		try {
			out.reset();
			out.write(keep_alive);
			
			toPeer.write(out.toByteArray());
		} catch (Exception e) {
			System.err.println("Failed to keep alive: " + e);
		}
	}
	
	public void readBitfield() {
		byte[] data = new byte [5];
		try {
			fromPeer.read(data);
			
			int x = data[3];
			
			byte[] bit = new byte[x];
			fromPeer.read(bit);
			
		} catch (Exception e) {
			System.err.println("Failed to readBitField: " + e);
		}
	}
	
	public void sendBitfield () {
		
		byte[] bitfield = new byte [(int) Math.ceil(RUBTClient.getTorrentInfo().piece_hashes.length / 8.0)];
		byte[][] temp = RUBTClient.getDownloadedPieces();
		boolean have = true;
		int y = 0;
		
		
		for (int i = 0; i < temp.length; i++) {
			
			if ( temp[i] == null )
				 have = false;
			
			if ( (i+1)% 8 == 0) {
				if (have) {
					bitfield [y] = 1;
				} else {
					bitfield [y] = 0;
					have = true;
				}
				y++;
			}
			
		}
		
		
		try {
			out.reset();
			int x = 1 + bitfield.length;
			byte[] len = {0,0,0};
			
			out.write(len);
			out.write(x);
			out.write(5);
			out.write(bitfield);
			
			toPeer.write(out.toByteArray());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public boolean showInterest() {
		byte[] data = new byte[5];
		try {
			out.flush();
			out.write(length_prefix);
			out.write(KEY_INTERESTED);
			
			toPeer.write(out.toByteArray());
			fromPeer.readFully(data, 0, data.length);
			
			//Server sent back the choke message
			//System.out.println("Did server send back unchoke?: " + Arrays.toString(data));
			
			out.reset();
			out.write(length_prefix);
			out.write(KEY_UNCHOKE);
			
			if(Arrays.equals(data, out.toByteArray())){
				choking = false;
				interested = true;
				peer_choking = false;
				peer_interested = true;
				return true;
			}
			
		} catch (Exception e) {
			System.err.println("Failed to show interest: " + e);
		}
		
		return false;
	}
	
	public boolean peerInterest () {
		byte[] data = new byte[5];
		
		try {
			out.reset();
			out.write(length_prefix);
			out.write(KEY_INTERESTED);
			
			
			fromPeer.readFully(data, 0, data.length);
//			fromPeer.read(data);
			
			System.out.println("Actual peer interest: " + Arrays.toString(data));
			System.out.println("Peer interest template: " + Arrays.toString(out.toByteArray()));
			if (Arrays.equals(data, out.toByteArray())) {
				return true;
			}
			
		} catch (Exception e) {
			System.err.println("Failed to get interested message: " + e);
		}
		
		return false;
	}
	
	public void uninterested() {

		try {
			out.reset();
			out.write(length_prefix);
			out.write(KEY_UNINTERESTED);
			toPeer.write(out.toByteArray());
			
			interested = false;
			
		} catch (Exception e) {
			System.err.println("Failed to uninterested: " + e);
		}	
	}
	
	public void choke() {
		
		try {
			out.reset();
			out.write(length_prefix);
			out.write(KEY_CHOKE);
			toPeer.write(out.toByteArray());
			
			choking = true;
			
		} catch (Exception e) {
			System.err.println("Failed to choke: " + e);
		}	
	}
	
	public void unchoke() {
		try {
			out.reset();
			out.write(length_prefix);
			out.write(KEY_UNCHOKE);
			toPeer.write(out.toByteArray());
			//System.out.println(Arrays.toString(out.toByteArray()));
			choking = false;
			
		} catch (Exception e) {
			System.err.println("Failed to unchoke: " + e);
		}	
	}
	
	public boolean isChoking() {
		return choking;
	}
	
	public boolean isInterested() {
		return interested;
	}
	
	public boolean Peer_choking() {
		return peer_choking;
	}
	
	public boolean Peer_interested() {
		return peer_interested;
	}
	
	public static byte[] getKeepAlive() {
		return keep_alive;
	}


	public static byte[] getLengthPrefix() {
		return length_prefix;
	}


	public static byte[] getHavePrefix() {
		return have_prefix;
	}


	public static byte[] getRequestLength() {
		return request_length;
	}
	
}
