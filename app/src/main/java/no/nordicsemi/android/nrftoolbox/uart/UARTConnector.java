/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrftoolbox.uart;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.traceappproject_daram.data.Result;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileService;
//UARTLogFragment의 변형
public class UARTConnector {
	private static final String SIS_LOG_SCROLL_POSITION = "sis_scroll_position";
	private static final int LOG_SCROLL_NULL = -1;
	private static final int LOG_SCROLLED_TO_BOTTOM = -2;

	private static final int LOG_REQUEST_ID = 1;
	private static final String[] LOG_PROJECTION = {LogContract.Log._ID, LogContract.Log.TIME, LogContract.Log.LEVEL, LogContract.Log.DATA};
	//같은 객체 쓰기

	private Result result;
	public static final byte arr[] = new byte[1000000];

	/**
	 * The service UART interface that may be used to send data to the target.
	 */
	private UARTInterface uartInterface;
	/**
	 * The adapter used to populate the list with log entries.
	 */

	/**
	 * The log session created to log events related with the target device.
	 */

	/**
	 * The last list view position.
	 */
	public static int connectionMode =0;
	public static boolean LEFT_INIT_ACTIVATED = false;
	public static boolean RIGHT_INIT_ACTIVATED = false;
	public static boolean LEFT_RECEIVE_ACTIVATED = false;
	public static boolean RIGHT_RECEIVE_ACTIVATED = false;
	private int logScrollPosition;
	private UARTActivity mother;
	public UARTConnector(UARTActivity mother,int connectionMode){
		this.mother = mother;
		this.connectionMode = connectionMode;
		onCreate();
	}
	public static boolean opened = false;

	public void sendDataInitially(byte mode){
		if(opened){
			return;
		}
		opened = true;

		Log.i(TAG,"sendDataInitially : "+(char)mode);
		Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				final String ml = "" + (char) mode; //errorneous
				uartInterface.send(ml);
			}
		}, 7000);

	}
	public void sendData(byte mode){
		final String ml = "" + (char) mode; //errorneous
		uartInterface.send(ml);
	}

	/**
	 * The receiver that listens for {@link BleProfileService#BROADCAST_CONNECTION_STATE} action.
	 */
	private final BroadcastReceiver commonBroadcastReceiver = new BroadcastReceiver() {
		@RequiresApi(api = Build.VERSION_CODES.M)
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// This receiver listens only for the BleProfileService.BROADCAST_CONNECTION_STATE action, no need to check it.
			final int state = intent.getIntExtra(BleProfileService.EXTRA_CONNECTION_STATE, BleProfileService.STATE_DISCONNECTED);
			Log.i(TAG,"receiver onreceive called");


			switch (state) {
				case BleProfileService.STATE_CONNECTED: {
					Log.i("UARTLogFragment","state connected");
					onDeviceConnected();
					break;
				}
				case BleProfileService.STATE_DISCONNECTED: {
					onDeviceDisconnected();
					break;
				}
				case BleProfileService.STATE_CONNECTING:
					break;

				case BleProfileService.STATE_DISCONNECTING:

					// current implementation does nothing in this states
					//모드에 맞게 파싱할 수 있어야댐
					break;
				case BleProfileService.CUSTOM_READY:
					sendData(Cons.MODE_MEASURE_LEFT);
					break;
				case BleProfileService.CUSTOM_LEFT_INIT_DONE:
					//sendData(Cons.MODE_RUN);//근데 이건 uartconnector를 새로 만들어야할듯
					//이걸 받았을 때 왼발이 끊어진 상태가 아닐수도 잇슴
					//mother.makeScanNConnect();
					Log.i(TAG,"receiver CUSTOM_LEFT_INIT_DONE received");
					onStop();


					break;
				case BleProfileService.CUSTOM_RIGHT_INIT_DONE:
					
					break; 
					case BleProfileService.CUSTOM_LEFT_DATA_DONE:

						break;
				default:
					// there should be no other actions
					break;
			}
		}
	};
	//logger 관련된 모든 것들 지웟다
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder service) {
			Log.i(TAG,"onServiceConnected called");
			final UARTService.UARTBinder bleService = (UARTService.UARTBinder) service;
			uartInterface = bleService;

			// and notify user if device is connected
			if (bleService.isConnected())
				onDeviceConnected();
		}

		@Override
		public void onServiceDisconnected(final ComponentName name) {
			Log.i(TAG,"onServiceDisconnected called");
			onDeviceDisconnected();

			uartInterface = null;
		}
	};
	//bundle 받는 거 삭제
	//이미 UARTActivity에 receiver를 등록해놓은 것
	public void onCreate() {
		LocalBroadcastManager.getInstance(mother).registerReceiver(commonBroadcastReceiver, makeIntentFilter());
	}

	public void onStart() {

		/*
		 * If the service has not been started before the following lines will not start it. However, if it's running, the Activity will be bound to it
		 * and notified via serviceConnection.
		 */
		final Intent service = new Intent(mother, UARTService.class);
		mother.bindService(service, serviceConnection, 0); // we pass 0 as a flag so the service will not be created if not exists
	}

	public void onStop() {

		try {
			mother.unbindService(serviceConnection);
			uartInterface = null;
		} catch (final IllegalArgumentException e) {
			// do nothing, we were not connected to the sensor
		}
	}

	public void onDestroy() {
		LocalBroadcastManager.getInstance(mother).unregisterReceiver(commonBroadcastReceiver);

	}




	/**
	 * Method called when user selected a device on the scanner dialog after the service has been started.
	 * Here we may bind this fragment to it.
	 */
	public void onServiceStarted() {
		// The service has been started, bind to it
		final Intent service = new Intent(mother, UARTService.class);
		mother.bindService(service, serviceConnection, 0);
	}

	String TAG = "UARTConnector";
	//0으로 왼발 on
	//2로 오른발 on
	protected void onDeviceConnected() {
		Log.i(TAG,"onDeviceConnected called");
		/*
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Log.i(TAG,"onDeviceConnected sleep end");

		 */
		Log.i(TAG,"connector connectionMode : "+connectionMode);
		switch (connectionMode){
			case 0:
				sendDataInitially(Cons.MODE_RUN);
				break;
			case 1:
				//sendDataInitially(Cons.MODE_RUN);
				break;
			case 2:
				//sendDataInitially(Cons.MODE_MEASURE_LEFT);
				break;
			case 3:
				//sendDataInitially(Cons.MODE_MEASURE_RIGHT);
				break;
		}
	}

	/**
	 * Method called when user disconnected from the target UART device or the connection was lost.
	 */
	protected void onDeviceDisconnected() {
		/*
		field.setEnabled(false);
		sendButton.setEnabled(false);

		 */
	}

	private static IntentFilter makeIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BleProfileService.BROADCAST_CONNECTION_STATE);
		return intentFilter;
	}
}
