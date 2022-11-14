import javax.swing.*;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

//import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.awt.event.*;
import java.io.*;

import com.amazonaws.regions.Regions;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.File;
import java.nio.file.Paths;

public class DragDropFiles extends JFrame {

	private DefaultListModel model = new DefaultListModel();
	private static DefaultTreeModel s3list = null;
	private int count = 0;
	private JTree tree;
	private static JLabel label;
	private JButton download;
	private JButton createBucket;
	private DefaultTreeModel treeModel;
	private TreePath namesPath;
	private JPanel wrap;
	private TreePath downloadPath = null;
	private static List<BucketWrapper> bucketWrappers = null;
	final static AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.DEFAULT_REGION).build();

	//Get the tree for display
	public static DefaultTreeModel buildS3TreeModel(AWSCredentials credentials) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("All My Buckets");
		DefaultMutableTreeNode parent;
		DefaultMutableTreeNode child;

		List<Bucket> buckets = getBuckets();
		for (Bucket b : buckets) {
			List<S3ObjectSummary> bucketObject = getBucketObjects(b.getName());
			parent = new DefaultMutableTreeNode(b.getName());
			root.add(parent);
			for (S3ObjectSummary o : bucketObject) {
				child = new DefaultMutableTreeNode(o.getKey());
				parent.add(child);
			}

		}
		return new DefaultTreeModel(root);
	}

	//Get a list of all the buckets
	public static List<Bucket> getBuckets() {
		List<Bucket> buckets = s3.listBuckets();
		System.out.println("Your Amazon S3 buckets are:");
		for (Bucket b : buckets) {

			System.out.println("* " + b.getName());
			BucketWrapper bw = new BucketWrapper(b);
			bw.setObjectList(getBucketObjects(b.getName()));
		}
		return buckets;
	}

	//Get a list of all the objects inside a given bucket's name
	public static List<S3ObjectSummary> getBucketObjects(String bucket_name) {
		ListObjectsV2Result result = s3.listObjectsV2(bucket_name);
		List<S3ObjectSummary> objects = result.getObjectSummaries();
		for (S3ObjectSummary os : objects) {
			System.out.println("\n" + os.toString() + " in bucket: " + os.getBucketName());
			;
		}
		return objects;
	}
	
	//constructor
	public DragDropFiles() {
        super("Drag and Drop File Transfers in Cloud");
        treeModel = buildS3TreeModel(getAWSCredentials());      
        tree = new JTree(treeModel);
        tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setDropMode(DropMode.ON);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        namesPath = tree.getPathForRow(2);
        tree.expandRow(2);
        tree.expandRow(1);
        tree.setRowHeight(0);

        //Handles the tree node selection event that triggered by user selection
        //Identify which tree node(file name) has been selected, for downloading.
        //For more info, see TreeSelectionListener Class in Java
        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                //DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                                   //tree.getLastSelectedPathComponent();

                /* if nothing is selected */ 
                //if (node == null) return;

                /* retrieve the node that was selected */ 
                //Object nodeInfo = node.getUserObject();
                //System.out.println("Node selected is:" + nodeInfo.toString());
                /* React to the node selection. */
                downloadPath = e.getNewLeadSelectionPath();
            }
        });
        
        tree.setTransferHandler(new TransferHandler() {

            public boolean canImport(TransferHandler.TransferSupport info) {
                // we'll only support drops (not clip-board paste)
                if (!info.isDrop()) {
                    return false;
                }
                info.setDropAction(COPY); //Tony added
                info.setShowDropLocation(true);
                // we import Strings and files
                if (!info.isDataFlavorSupported(DataFlavor.stringFlavor) &&
                		!info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }

                // fetch the drop location
                JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
                TreePath path = dl.getPath();

                // we don't support invalid paths or descendants of the names folder
                if (path == null || namesPath.isDescendant(path)) {
                    return false;
                }
                return true;
            }

            public boolean importData(TransferHandler.TransferSupport info) {            	
            		// if we can't handle the import, say so
                if (!canImport(info)) {
                    return false;
                }
                // fetch the drop location
                JTree.DropLocation dl = (JTree.DropLocation)info.getDropLocation();
                
                // fetch the path and child index from the drop location
                TreePath path = dl.getPath();
                int childIndex = dl.getChildIndex();

                // fetch the data and bail if this fails
                String uploadName = "";
                
                Transferable t = info.getTransferable();
                try {
                    java.util.List<File> l =
                        (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);

                    for (File f : l) {
                    		uploadName = f.getName();
                    		String copyName = "./copy-" + f.getName();
                    		File destFile = new File(copyName);
                    		copyFile(f, destFile);
                        break;//We process only one dropped file.
                    }
                } catch (UnsupportedFlavorException e) {
                    return false;
                } catch (IOException e) {
                    return false;
                }
                
                // if child index is -1, the drop was on top of the path, so we'll
                // treat it as inserting at the end of that path's list of children
                if (childIndex == -1) {
                    childIndex = tree.getModel().getChildCount(path.getLastPathComponent());
                }

                // create a new node to represent the data and insert it into the model
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(uploadName);
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)path.getLastPathComponent();
                treeModel.insertNodeInto(newNode, parentNode, childIndex);

                // make the new node visible and scroll so that it's visible
                tree.makeVisible(path.pathByAddingChild(newNode));
                tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));
				
                //Display uploading status
                label.setText("UpLoaded **" + uploadName + "** successfully!");

                return true;
            }
            
        });

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        this.wrap = new JPanel();
        this.label = new JLabel("Status Bar...");
        wrap.add(this.label);
        p.add(Box.createHorizontalStrut(4));
        p.add(Box.createGlue());
        p.add(wrap);
        p.add(Box.createGlue());
        p.add(Box.createHorizontalStrut(4));
        getContentPane().add(p, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(tree), BorderLayout.CENTER);
        download = new JButton("Download");
        //This is the action that occurs when the user clicks the download button
        download.addActionListener(new ActionListener() { 
        	public void actionPerformed(ActionEvent e) {
        		//If there is an item selected, download that item
        	    if(downloadPath != null) {
        	    	JOptionPane.showMessageDialog(null, "Would you like to downloand file " + 
        	    		downloadPath.getLastPathComponent().toString() + " from cloud from buckets?");
        	    	//Get bucket and key names
        	    	String bucket_name = downloadPath.getParentPath().getLastPathComponent().toString();
        	    	String key_name = downloadPath.getLastPathComponent().toString();
        	    	//Try to download the file
    	    		try {
    	    			label.setText("Downloading...");
    	    		    S3Object o = s3.getObject(bucket_name, key_name);
    	    		    S3ObjectInputStream s3ObjectInputStream = o.getObjectContent();
    	    		    FileOutputStream fileOutputStream = new FileOutputStream(new File(key_name));
    	    		    byte[] buffer = new byte[1024];
    	    		    int read_len = 0;
    	    		    while ((read_len = s3ObjectInputStream.read(buffer)) > 0) {
    	    		    	fileOutputStream.write(buffer, 0, read_len);
    	    		    	
    	    		    }
    	    		    label.setText("Downloaded **" + key_name + "** successfully!");
    	    		    s3ObjectInputStream.close();
    	    		    fileOutputStream.close();
    	    		} catch (AmazonServiceException ae) {
    	    		    System.err.println(ae.getErrorMessage());
    	    		    System.exit(1);
    	    		} catch (FileNotFoundException ae) {
    	    		    System.err.println(ae.getMessage());
    	    		    System.exit(1);
    	    		} catch (IOException ae) {
    	    		    System.err.println(ae.getMessage());
    	    		    System.exit(1);
    	    		}
        	    }
        	}
        });
        createBucket = new JButton("Create Bucket");
        createBucket.addActionListener(new ActionListener() { 
        	public void actionPerformed(ActionEvent e) {
        		//If there is an item selected, download that item
//        	    if(downloadPath != null) {
//        	    	JOptionPane.showMessageDialog(null, "Would you like to create a bucket called: " + 
//        	    		downloadPath.getLastPathComponent().toString() + " from cloud from buckets?");
//        	    	//Get bucket and key names
//        	    	
//        	    }
        	}
        });
        p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        wrap = new JPanel();
        //wrap.add(new JLabel("Show drop location:"));
        wrap.add(download);
        p.add(Box.createHorizontalStrut(4));
        p.add(Box.createGlue());
        p.add(wrap);
        p.add(Box.createGlue());
        p.add(Box.createHorizontalStrut(4));
        getContentPane().add(p, BorderLayout.SOUTH);

        getContentPane().setPreferredSize(new Dimension(400, 450));
    }

    private static void increaseFont(String type) {
        Font font = UIManager.getFont(type);
        font = font.deriveFont(font.getSize() + 4f);
        UIManager.put(type, font);
    }

    private static void createAndShowGUI() {
        //Create and set up the window.
        DragDropFiles test = new DragDropFiles();
        test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        //Display the window.
        test.pack();
        test.setVisible(true);
    }
    
    
    private void copyFile(File source, File dest)
    		throws IOException {
	    	InputStream input = null;
	    	OutputStream output = null;
	    	try {
	    		input = new FileInputStream(source);
	    		output = new FileOutputStream(dest);
	    		byte[] buf = new byte[1024];
	    		int bytesRead;
	    		while ((bytesRead = input.read(buf)) > 0) {
	    			output.write(buf, 0, bytesRead);
	    		}
	    	} finally {
	    		input.close();
	    		output.close();
	    	}
    }

    public static void main(String[] args) {
    	
    	AWSCredentials credentials = getAWSCredentials();
        s3list = buildS3TreeModel(credentials);

//    	 putObject("test-from-main-schuy","testFiles/testFile1.txt");
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {                
                try {
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                    increaseFont("Tree.font");
                    increaseFont("Label.font");
                    increaseFont("ComboBox.font");
                    increaseFont("List.font");
                } catch (Exception e) {}

                //Turn off metal's use of bold fonts
	        UIManager.put("swing.boldMetal", Boolean.FALSE);
                createAndShowGUI();
            }
        });
    }
    
    public static AWSCredentials getAWSCredentials() {
    	AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (/Users/schuylerasplin/.aws/credentials), and is in valid format.",
                    e);
        }
        return credentials;
    }
    
    public static void putObject(String bucket_name, String file_path) {
    	final String USAGE = "\n" +
                "To run this example, supply the name of an S3 bucket and a file to\n" +
                "upload to it.\n" +
                "\n" +
                "Ex: PutObject <bucketname> <filename>\n";
          	
    	
        String key_name = Paths.get(file_path).getFileName().toString();
//        System.out.println("pwd = "+Paths.get("").toAbsolutePath().toString());

        System.out.format("Uploading %s to S3 bucket %s...\n", file_path, bucket_name);
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.DEFAULT_REGION).build();
        try {
            s3.putObject(bucket_name, key_name, new File(file_path));
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }
    }
    
}
