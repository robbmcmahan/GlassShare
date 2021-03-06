package com.w9jds.glassshare;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import android.accounts.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.google.android.glass.widget.CardScrollView;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.w9jds.glassshare.Adapters.csaAdapter;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import com.w9jds.glassshare.Classses.ConnectedThread;

@SuppressLint("DefaultLocale")
public class MainActivity extends Activity 
{
    private BluetoothAdapter mBluetoothAdapter;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

	public static final String CAMERA_IMAGE_BUCKET_NAME = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Camera";
	public static final String CAMERA_IMAGE_BUCKET_ID = getBucketId(CAMERA_IMAGE_BUCKET_NAME);

    private static Drive mdService;
    private GoogleAccountCredential mgacCredential;
	
	//custom adapter
	private csaAdapter mcvAdapter;
	//list for all the paths of the images on google glass
	private ArrayList<String> mlsPaths = new ArrayList<String>();
	//variable for the last selected index
	private int iPosition;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		//get all the images from the camera folder (paths)
		mlsPaths = getCameraImages(this);
        //sort the paths of pictures
        sortPaths();
		//create a new card scroll viewer for this context
		CardScrollView csvCardsView = new CardScrollView(this);
		//create a new adapter for the scroll viewer
		mcvAdapter = new csaAdapter(this, mlsPaths);
		//set this adapter as the adapter for the scroll viewer
		csvCardsView.setAdapter(mcvAdapter);
		//activate this scroll viewer
		csvCardsView.activate();
		//add a listener to the scroll viewer that is fired when an item is clicked
        csvCardsView.setOnItemClickListener(new OnItemClickListener() 
        {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				//save the card index that was selected
				iPosition = position;
				//open the menu
				openOptionsMenu();
            }
         });
		
        //set the view of this activity
		setContentView(csvCardsView);
    }

    private void sortPaths()
    {
        java.io.File[] fPics = new java.io.File[mlsPaths.size()];

        for (int i = 0; i < mlsPaths.size(); i++)
            fPics[i] = new java.io.File(mlsPaths.get(i));

        mlsPaths.clear();

        Arrays.sort(fPics, new Comparator<java.io.File>()
        {
            @Override
            public int compare(java.io.File o1, java.io.File o2)
            {
                return Long.valueOf(o1.lastModified()).compareTo(o2.lastModified());
            }
        });

        for (int i = fPics.length - 1; i >= 0; i--)
            mlsPaths.add(fPics[i].getAbsolutePath());
    }


	
	public static String getBucketId(String path) 
	{
	    return String.valueOf(path.toLowerCase().hashCode());
	}

	public static ArrayList<String> getCameraImages(Context context) 
	{
	    final String[] projection = { MediaStore.Images.Media.DATA };
	    final String selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
	    final String[] selectionArgs = { CAMERA_IMAGE_BUCKET_ID };
	    final Cursor cursor = context.getContentResolver().query(Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null);
	    ArrayList<String> result = new ArrayList<String>(cursor.getCount());
	    
	    if (cursor.moveToFirst()) 
	    {
	        final int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	        do 
	        {
	            final String data = cursor.getString(dataColumn);
	            result.add(data);
	        } while (cursor.moveToNext());
	    }
	    
	    cursor.close();
	    return result;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) 
	{
		switch (item.getItemId()) 
		{
	        case R.id.delete_menu_item:
	        	//pull the file from the path of the selected item
	        	java.io.File fPic = new java.io.File(mlsPaths.get(iPosition));
	        	//delete the image
	        	fPic.delete();
	        	//refresh the folder
	        	sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" +  Environment.getExternalStorageDirectory())));
	        	//remove the selected item from the list of images
	        	mlsPaths.remove(iPosition);
	        	//let the adapter know that the list of images has changed
	        	mcvAdapter.notifyDataSetChanged();
	        	//handled
	            return true;
	        case R.id.upload_menu_item:
                //get google account credentials and store to member variable
                mgacCredential = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
                //get a list of all the accounts on the device
                Account[] myAccounts = AccountManager.get(this).getAccounts();
                //for each account
                for(int i = 0; i < myAccounts.length; i++)
                {
                    //if the account type is google
                    if (myAccounts[i].type.equals("com.google"))
                        //set this as the selected Account
                        mgacCredential.setSelectedAccountName(myAccounts[i].name);
                }
                //get the drive service
                mdService = getDriveService(mgacCredential);
                //save the selected item to google drive
                saveFileToDrive(mlsPaths.get(iPosition));
	        	return true;
            case R.id.share_menu_item:

                // start Facebook Login
//                Account[] Accounts = AccountManager.get(this).getAccounts();

                return true;
            case R.id.phone_menu_item:
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                if(mBluetoothAdapter.isEnabled())
                {
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

                    Thread btThread = new Thread(new ConnectThread((BluetoothDevice)pairedDevices.toArray()[0]));
                    btThread.run();

//                    ConnectThread test = new ConnectThread((BluetoothDevice)pairedDevices.toArray()[0]);
//                    test.run();
                }

                return true;

	        default:
	            return super.onOptionsItemSelected(item);
		}
	};

    private void saveFileToDrive(String sPath)
    {
        final String msPath = sPath;

        Thread t = new Thread(new Runnable()
        {
            @Override
            public void run() {
                try
                {
                    // File's binary content
                    java.io.File fImage = new java.io.File(msPath);
                    FileContent fcContent = new FileContent("image/jpeg", fImage);

                    // File's metadata.
                    File gdfBody = new File();
                    gdfBody.setTitle(fImage.getName());
                    gdfBody.setMimeType("image/jpeg");

                    com.google.api.services.drive.model.File gdfFile = mdService.files().insert(gdfBody, fcContent).execute();
                    if (gdfFile != null)
                        Log.d("GlassShareUploadTask", "Uploaded");
                }
                catch (UserRecoverableAuthIOException e)
                {
                    Log.d("GlassShareUploadTask", e.toString());
//                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                }
                catch (IOException e)
                {
                    Log.d("GlassShareUploadTask", e.toString());
//                    e.printStackTrace();
                }
                catch (Exception e)
                {
                    Log.d("GlassShareUploadTask", e.toString());
                }
            }
        });
        t.start();

    }

    private Drive getDriveService(GoogleAccountCredential credential)
    {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();
    }


    private class ConnectThread extends Thread
    {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device)
        {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try
            {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            }

            catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run()
        {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try
            {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            }
            catch (IOException connectException)
            {
                // Unable to connect; close the socket and get out
                try
                {
                    mmSocket.close();
                }

                catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            ConnectedThread ctThread = new ConnectedThread(mmSocket);

            Bitmap bmp = BitmapFactory.decodeFile(mlsPaths.get(iPosition));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();

            ctThread.write(byteArray);
            ctThread.cancel();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel()
        {
            try
            {
                mmSocket.close();
            }

            catch (IOException e) { }
        }
    }
}



