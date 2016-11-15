//Jacob Rizer

package Main;

public class Peer {

	private String peer_ID;
	private String IP;
	private int port;
	private boolean unchoked;
	
	public Peer(String peer_ID, String IP, int port){
		this.peer_ID = peer_ID;
		this.IP = IP;
		this.port = port;
	}
	
	public Peer(String IP, int port){
		this.IP = IP;
		this.port = port;
	}
	
	
	
	
	
	
	
	public String getPeer_ID() {
		return peer_ID;
	}
	public void setPeer_ID(String peer_ID) {
		this.peer_ID = peer_ID;
	}
	public String getIP() {
		return IP;
	}
	public void setIP(String iP) {
		IP = iP;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}

	public boolean isUnchoked() {
		return unchoked;
	}

	public void setUnchoked(boolean unchoked) {
		this.unchoked = unchoked;
	}
	
	
}
