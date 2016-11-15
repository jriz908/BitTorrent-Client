package Main;

import GivenTools.*;
import java.io.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.net.URL;
import java.net.HttpURLConnection;






/**
 * @author Jacob Rizer
 * 
 * This class is for our main method.
 * We pass in a torrent file and a name
 * for the new file that we will create. With
 * the torrentInfo object we are able to create a 
 * Torrent and download the file.
 *
 */
public class RUBTClient {

	public static void main(String[] args) throws IOException, BencodingException, InterruptedException {
		
		//must pass in two arguments
		if(args.length != 2){
			System.out.println("Need to pass in a torrent file and a file to write to.");
			System.exit(1);;
		}
		
		File torrentFile = new File(args[0]);
		
		if(!torrentFile.canRead()){
			System.out.println("Torrent file can not be read");
			System.exit(1);
		}
		
		//store torrent file into bytes array
		byte[] torrentBytes = new byte[(int)torrentFile.length()];

		//System.out.println(torrentBytes.length);

		DataInputStream stream = new DataInputStream(new FileInputStream(torrentFile));
		
		//read torrent Bytes
		stream.readFully(torrentBytes);
		stream.close();


		//Create TorrentInfo object with torrentBytes array
		TorrentInfo torrentInfo = new TorrentInfo(torrentBytes);
		
		String filename = args[1];
		
		//create my torrent object
		Torrent torrent = new Torrent(torrentInfo, filename);
		
		//set URL for messaging tracker
		torrent.setTrackerURL();
		
		torrent.sendRequestToTracker();
		
		//for debugging
		//torrent.printTrackerResponse();
		
		//filter out the peers that do not have the -RU prefix
		torrent.setPeers();
		
		//start our download
		torrent.start();
		
		//clean up at the end
		torrent.close();
		
		
		
	}

}
