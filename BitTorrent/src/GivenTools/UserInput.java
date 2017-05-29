package GivenTools;


import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;


public class UserInput implements Runnable{
	private static int percent;
	private static JProgressBar progressBar;
	private static JDialog f;
	private static boolean stop;
	/**
	*	Write TxtNum to txtfile
	*	Write(Append?) all downloaded pieces the file stream. Make sure the next time the file stream is open, the downloaded pieces are still there.
	* 		
	* 
	*/
	public UserInput() {
		stop = false;
	}
	
	public void run () {
		f = new JDialog();
		f.setTitle("Bit Client");
		Container content = f.getContentPane();
		progressBar = new JProgressBar();
		JButton start = new JButton ("Start");
		JButton quit = new JButton ("Quit");
		
		start.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	           start();
	         }          
	      });
		
		quit.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	            quit();
	         }          
	      });
		
		progressBar.setStringPainted(true);
		Border border = BorderFactory.createTitledBorder("Dowload Progress...");
		progressBar.setBorder(border);
		content.add(progressBar, BorderLayout.NORTH);
		content.add(start, BorderLayout.WEST);
		content.add(quit, BorderLayout.EAST);
		f.setSize(400, 120);
		f.setLocationRelativeTo(null);
		f.setVisible(true);
		f.setModal(true);
		
		user_commands();
		
	}
	
	public void user_commands () {
		
		while ( RUBTClient.getProgress() < RUBTClient.getTorrentInfo().piece_hashes.length && !stop) {
			percent = (int) ( ((double)RUBTClient.getProgress() / RUBTClient.getTorrentInfo().piece_hashes.length) * 100);
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			progressUpdate(percent);
		}
		
		f.setVisible(false);
		f.dispose();
		return;
	}
	
	public void progressUpdate(final int percent) {
	     SwingUtilities.invokeLater(new Runnable() {
	         public void run() {
	             progressBar.setValue(percent);
	         }
	     });
	}
	
	public static void start () {
		RUBTClient.setStart(true);
	}
	
	public static void quit () {
		RUBTClient.setStop(true);
		stop = true;
		f.setVisible(false);
	}
	
}
