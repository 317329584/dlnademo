package com.charon.cyberlink;

import java.util.ArrayList;
import java.util.List;

import org.cybergarage.upnp.Device;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.charon.cyberlink.engine.DLNAContainer;
import com.charon.cyberlink.engine.MultiPointController;
import com.charon.cyberlink.engine.DLNAContainer.DeviceChangeListener;
import com.charon.cyberlink.inter.IController;
import com.charon.cyberlink.service.DLNAService;
import com.charon.cyberlink.util.LogUtil;
import com.charon.cyberlink.util.Util;
import com.example.screendemo.R;

public class ControlActivity extends Activity implements OnClickListener {
	private IController mController;
	private TextView tv_title;
	private SeekBar sb_progress;
	private TextView tv_current;
	private TextView tv_total;
	private ImageView iv_pre;
	private ImageView iv_next;
	private ImageView iv_play;
	private ImageView iv_pause;
	private ImageView iv_back_fast;
	private ImageView iv_go_fast;
	private SeekBar sb_voice;
	private ImageView iv_mute;
	private ImageView iv_volume;
	private FrameLayout fl_play;
	private FrameLayout fl_volume;

	private Device mDevice;

	private List<String> urls = new ArrayList<String>();
	private List<String> titles = new ArrayList<String>();
	private int index;
	private static final String ZEROTIME = "00:00:00";
	private static final String MUTE = "1";
	private static final String UNMUTE = "0";
	private int mMediaDuration;
	private static final int RETRY_TIME = 1000;
	private static final String NOT_IMPLEMENTED = "NOT_IMPLEMENTED";
	private boolean mPaused;
	private boolean mPlaying;
	private static final int AUTO_INCREASING = 8001;
	private static final int AUTO_PLAYING = 8002;
	private boolean mStartAutoPlayed;
	private static final String TAG = "ControlActivity";

	private ListView listView;
	private List<Device> mDevices;
	private DeviceAdapter adapter;
	ProgressDialog pro;

	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case AUTO_INCREASING:
				stopAutoIncreasing();
				getPositionInfo();
				startAutoIncreasing();
				break;

			case AUTO_PLAYING:
				playNext();
				break;
			}
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_control);
		findView();
		initDLNAService();
	}

	/**
	 * 初始化dlna查找服务
	 */
	private void initDLNAService() {
		Intent intent = new Intent(getApplicationContext(), DLNAService.class);
		startService(intent);
		mDevices = DLNAContainer.getInstance().getDevices();
		adapter = new DeviceAdapter();
		listView.setAdapter(adapter);
		pro = new ProgressDialog(ControlActivity.this);
		if (mDevices.size() <= 0) {
			pro.show();
		}

		DLNAContainer.getInstance().setDeviceChangeListener(
				new DeviceChangeListener() {

					@Override
					public void onDeviceChange(Device device) {
						runOnUiThread(new Runnable() {
							public void run() {
								if (pro.isShowing()) {
									pro.dismiss();
								}
								// 初始化列表
								refresh();
							}
						});
					}
				});

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				DLNAContainer.getInstance().setSelectedDevice(
						mDevices.get(position));
				startPlay();
				updateVoice();
				getMute();
			}
		});
	}

	private void refresh() {
		mDevices = DLNAContainer.getInstance().getDevices();
		//移除不能控制播放的device
//		List<Device> devices = new ArrayList<Device>()	;
// 		for (int i = 0; i < mDevices.size(); i++) {
//			if(mDevices.get(i).getLocation().contains("xml")){
//				devices.add(mDevices.get(i));
//			}
//		}
// 		mDevices.clear();
// 		mDevices.addAll(devices);
 		
 		
		if (adapter != null)
			adapter.notifyDataSetChanged();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		stop();
		stopDLNAService();
	}

	private void findView() {
		tv_title = (TextView) findViewById(R.id.tv_title);
		sb_progress = (SeekBar) findViewById(R.id.sb_progress);
		tv_current = (TextView) findViewById(R.id.tv_current);
		tv_total = (TextView) findViewById(R.id.tv_total);
		iv_pre = (ImageView) findViewById(R.id.iv_pre);
		iv_next = (ImageView) findViewById(R.id.iv_next);
		iv_play = (ImageView) findViewById(R.id.iv_play);
		iv_pause = (ImageView) findViewById(R.id.iv_pause);
		iv_back_fast = (ImageView) findViewById(R.id.iv_back_fast);
		iv_go_fast = (ImageView) findViewById(R.id.iv_go_fast);
		sb_voice = (SeekBar) findViewById(R.id.sb_voice);
		iv_mute = (ImageView) findViewById(R.id.iv_mute);
		iv_volume = (ImageView) findViewById(R.id.iv_volume);
		fl_play = (FrameLayout) findViewById(R.id.fl_play);
		fl_volume = (FrameLayout) findViewById(R.id.fl_volume);
		listView = (ListView) findViewById(R.id.devicesList);
		findViewById(R.id.head).setOnClickListener(this);
		findViewById(R.id.searchbut).setOnClickListener(this);
		setController(new MultiPointController());
	}

	/**
	 * 当连接设备成功后 初始化播放功能
	 */
	private void startPlay() {
		mDevice = DLNAContainer.getInstance().getSelectedDevice();
		urls.add("http://video19.ifeng.com/video06/2012/09/28/97b03b63-1133-43d0-a6ff-fb2bc6326ac7.mp4");// 伊能�?
		urls.add("rtmp://210.75.212.76:1935/bjgj/media/test/五大发展理念：新理念开创新思路.mp4");// 我们结婚�?
		urls.add("http://video19.ifeng.com/video06/2012/04/11/629da9ec-60d4-4814-a940-997e6487804a.mp4"); // 佟丽�?
		titles.add("000");
		titles.add("111");
		titles.add("2222");

		if (mController == null || mDevice == null) {
			// usually can't reach here.
			Toast.makeText(getApplicationContext(), "无效的操作", Toast.LENGTH_SHORT)
					.show();
			LogUtil.d(TAG, "Controller or Device is null, finish this activity");
			finish();
		}

		// init the state
		getMaxVolumn();

		sb_progress.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				startAutoIncreasing();
				int progress = seekBar.getProgress();
				seek(Util.secToTime(progress));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				stopAutoIncreasing();
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				tv_current.setText(Util.secToTime(progress));
				if (fromUser) {
					stopAutoIncreasing();
				}
			}
		});

		sb_voice.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setVoice(seekBar.getProgress());
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (progress == 0) {
					iv_mute.setVisibility(View.VISIBLE);
					iv_volume.setVisibility(View.GONE);
				} else {
					iv_mute.setVisibility(View.GONE);
					iv_volume.setVisibility(View.VISIBLE);
				}
			}
		});

		iv_pre.setOnClickListener(this);
		iv_next.setOnClickListener(this);
		iv_go_fast.setOnClickListener(this);
		iv_back_fast.setOnClickListener(this);
		fl_play.setOnClickListener(this);
		fl_volume.setOnClickListener(this);
		play(getCurrentPlayPath());
	}

	private void setController(IController controller) {
		this.mController = controller;
	}

	/**
	 * Get the max volume value and set it the sb_voice.
	 */
	private synchronized void getMaxVolumn() {
		new Thread() {
			public void run() {
				final int maxVolumnValue = mController
						.getMaxVolumeValue(mDevice);

				if (maxVolumnValue <= 0) {
					LogUtil.d(TAG, "get max volumn value failed..");
					sb_voice.setMax(100);
				} else {
					LogUtil.d(TAG,
							"get max volumn value success, the value is "
									+ maxVolumnValue);
					sb_voice.setMax(maxVolumnValue);
				}
			};
		}.start();
	}

	/**
	 * Get current voice value and set it the sb_voice.
	 */
	private void updateVoice() {
		new Thread() {
			@Override
			public void run() {
				int currentVoice = mController.getVoice(mDevice);
				if (currentVoice == -1) {
					currentVoice = 0;
					LogUtil.d(TAG, "get current voice failed");
				} else {
					LogUtil.d(TAG, "get current voice success");
					sb_voice.setProgress(currentVoice);
				}
			}
		}.start();
	}

	/**
	 * Get if is muted and set the mute image.
	 */
	private void getMute() {
		new Thread() {
			@Override
			public void run() {
				final String mute = mController.getMute(mDevice);
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						if (mute == null) {
							LogUtil.d(TAG, "get mute failed...");
							if (sb_voice.getProgress() == 0) {
								initMuteImg(MUTE);
							}
						} else {
							LogUtil.d(TAG, "get mute success");
							initMuteImg(mute);
						}

					}
				});
			}
		}.start();
	}

	/**
	 * Start to play the video.
	 * 
	 * @param path
	 *            The video path.
	 */
	private synchronized void play(final String path) {
		// Initial the state.
		mPaused = false;
		showPlay(true);
		tv_current.setText(ZEROTIME);
		tv_total.setText(ZEROTIME);
		setTitle(path);
		stopAutoIncreasing();
		stopAutoPlaying();

		new Thread() {
			public void run() {
				final boolean isSuccess = mController.play(mDevice, path);
				if (isSuccess) {
					LogUtil.d(TAG, "play success");
				} else {
					LogUtil.d(TAG, "play failed..");
				}

				runOnUiThread(new Runnable() {
					public void run() {
						LogUtil.d(TAG,
								"play success and start to get media duration");
						if (isSuccess) {
							mPlaying = true;
							startAutoIncreasing();
						}
						showPlay(!isSuccess);
						// Get the media duration and set it to the total time.
						getMediaDuration();
					}
				});

			};
		}.start();
	}

	private synchronized void pause() {
		stopAutoIncreasing();
		stopAutoPlaying();
		showPlay(true);

		new Thread() {
			public void run() {
				final boolean isSuccess = mController.pause(mDevice);

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						showPlay(isSuccess);
						if (isSuccess) {
							mPaused = true;
							mPlaying = false;
							mHandler.removeMessages(AUTO_PLAYING);
						} else {
							startAutoIncreasing();
						}
					}
				});
			};
		}.start();
	}

	private synchronized void playNext() {
		index++;
		if (index > urls.size() - 1) {
			index = 0;
		}
		play(getCurrentPlayPath());
	}

	private synchronized void playPre() {
		index--;
		if (index < 0) {
			index = urls.size() - 1;
		}
		play(getCurrentPlayPath());
	}

	private synchronized void goon(final String pausePosition) {
		new Thread() {
			@Override
			public void run() {
				final boolean isSuccess = mController.goon(mDevice,
						pausePosition);
				if (isSuccess) {
					mPlaying = true;
					LogUtil.d(TAG, "Go on to play success");
				} else {
					mPlaying = false;
					LogUtil.d(TAG, "Go on to play failed.");
				}
				runOnUiThread(new Runnable() {
					public void run() {
						showPlay(!isSuccess);
						if (isSuccess) {
							startAutoIncreasing();
						}
					}
				});
			}
		}.start();
	}

	/**
	 * Seek playing position to the target position.
	 * 
	 * @param targetPosition
	 *            target position like "00:00:00"
	 */
	private synchronized void seek(final String targetPosition) {
		new Thread() {
			@Override
			public void run() {
				boolean isSuccess = mController.seek(mDevice, targetPosition);
				if (isSuccess) {
					LogUtil.d(TAG, "seek success");
					sb_progress.setProgress(Util.getIntLength(targetPosition));
				} else {
					LogUtil.d(TAG, "seek failed..");
				}

				runOnUiThread(new Runnable() {
					public void run() {
						if (mPlaying) {
							startAutoIncreasing();
						} else {
							stopAutoIncreasing();
						}
					}
				});
			}
		}.start();
	}

	/**
	 * Get the current playing position.
	 */
	private synchronized void getPositionInfo() {
		new Thread() {
			@Override
			public void run() {
				String position = mController.getPositionInfo(mDevice);
				LogUtil.d(TAG, "Get position info and the value is " + position);
				if (TextUtils.isEmpty(position)
						|| NOT_IMPLEMENTED.equals(position)) {
					return;
				}
				final int currentPosition = Util.getIntLength(position);
				if (currentPosition <= 0 || currentPosition > mMediaDuration) {
					return;
				}
				sb_progress.setProgress(Util.getIntLength(position));

				runOnUiThread(new Runnable() {
					public void run() {
						if (currentPosition >= mMediaDuration - 3
								&& mMediaDuration > 0) {
							if (mStartAutoPlayed) {
								return;
							} else {
								mStartAutoPlayed = true;
								LogUtil.d(TAG, "start auto play next video");
								stopAutoPlaying();
								startAutoPlaying((mMediaDuration - currentPosition) * 1000);
							}
						}
					}
				});
			}
		}.start();
	}

	private synchronized void getMediaDuration() {
		new Thread() {
			@Override
			public void run() {
				final String mediaDuration = mController
						.getMediaDuration(mDevice);
				mMediaDuration = Util.getIntLength(mediaDuration);

				LogUtil.d(TAG, "Get media duration and the value is "
						+ mMediaDuration);
				runOnUiThread(new Runnable() {
					public void run() {
						if (TextUtils.isEmpty(mediaDuration)
								|| NOT_IMPLEMENTED.equals(mediaDuration)
								|| mMediaDuration <= 0) {
							mHandler.postDelayed(new Runnable() {

								@Override
								public void run() {
									LogUtil.e(TAG,
											"Get media duration failed, retry later."
													+ "Duration:"
													+ mediaDuration
													+ "intLength:"
													+ mMediaDuration);
									getMediaDuration();
								}
							}, RETRY_TIME);
							return;
						}
						tv_total.setText(mediaDuration);
						sb_progress.setMax(mMediaDuration);
					}
				});
			}
		}.start();
	}

	private synchronized void setMute(final String targetValue) {
		new Thread() {
			@Override
			public void run() {
				boolean isSuccess = mController.setMute(mDevice, targetValue);
				if (isSuccess) {
					runOnUiThread(new Runnable() {
						public void run() {
							initMuteImg(targetValue);
							getVoice();
						}
					});
				}
			}
		}.start();
	}

	private synchronized void setVoice(final int voice) {
		new Thread() {
			@Override
			public void run() {
				boolean isSuccess = mController.setVoice(mDevice, voice);
				if (isSuccess) {
					sb_voice.setProgress(voice);
					if (voice == 0) {
						initMuteImg(MUTE);
					}
				}
			}
		}.start();
	}

	private synchronized void getVoice() {
		new Thread() {
			@Override
			public void run() {
				int voice = mController.getVoice(mDevice);
				sb_voice.setProgress(voice);
				if (voice == 0) {
					initMuteImg(MUTE);
				}
			}
		}.start();
	}

	private synchronized void stop() {
		stopAutoPlaying();
		stopAutoIncreasing();
		new Thread() {
			@Override
			public void run() {
				final boolean isSuccess = mController.stop(mDevice);
				runOnUiThread(new Runnable() {
					public void run() {
						showPlay(isSuccess);
					}
				});
			}
		}.start();
	}

	@SuppressWarnings("unused")
	private synchronized void getTransportState() {
		new Thread() {
			@Override
			public void run() {
				String transportState = mController.getTransportState(mDevice);
				// Do your things here;
				LogUtil.d(TAG, "Get transportState :" + transportState);
			}
		}.start();
	}

	/**
	 * 快进或快�?
	 * 
	 * @param isGo
	 *            true表示快进，false为快�?
	 */
	private synchronized void fastGoOrBack(boolean isGo) {
		stopAutoIncreasing();
		String position = tv_current.getText().toString();
		int targetLength;
		if (isGo) {
			targetLength = Util.getIntLength(position) + 10;
			if (targetLength > mMediaDuration) {
				targetLength = mMediaDuration;
			}
		} else {
			targetLength = Util.getIntLength(position) - 10;
			if (targetLength < 0) {
				targetLength = 0;
			}
		}
		sb_progress.setProgress(targetLength);
		seek(Util.secToTime(targetLength));
	}

	private void startAutoIncreasing() {
		mHandler.sendEmptyMessageDelayed(AUTO_INCREASING, 1000);
	}

	private void stopAutoIncreasing() {
		mHandler.removeMessages(AUTO_INCREASING);
	}

	private void startAutoPlaying(long interTimes) {
		mHandler.sendEmptyMessageAtTime(AUTO_PLAYING, interTimes);
	}

	private void stopAutoPlaying() {
		mHandler.removeMessages(AUTO_PLAYING);
	}

	/**
	 * 显示播放或暂停方法
	 */
	private void showPlay(boolean showPlay) {
		if (showPlay) {
			iv_play.setVisibility(View.VISIBLE);
			iv_pause.setVisibility(View.GONE);
		} else {
			iv_play.setVisibility(View.GONE);
			iv_pause.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * 显示声音图片
	 */
	private void initMuteImg(String mute) {
		if (MUTE.equals(mute)) {
			iv_mute.setVisibility(View.VISIBLE);
			iv_volume.setVisibility(View.GONE);
			sb_voice.setProgress(0);
		} else if (UNMUTE.equals(mute)) {
			iv_mute.setVisibility(View.GONE);
			iv_volume.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * 获取当前播放地址
	 */
	private String getCurrentPlayPath() {
		return urls.get(index);
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
		case R.id.fl_play:
			if (mPlaying) {
				pause();
				return;
			}
			if (mPaused) {
				String pausePosition = tv_current.getText().toString().trim();
				goon(pausePosition);
			} else {
				play(getCurrentPlayPath());
			}
			break;
		case R.id.fl_volume:
			String targetValue = MUTE;
			if (iv_mute.getVisibility() == View.VISIBLE) {
				targetValue = UNMUTE;
				iv_mute.setVisibility(View.GONE);
				iv_volume.setVisibility(View.VISIBLE);
			} else {
				iv_mute.setVisibility(View.VISIBLE);
				iv_volume.setVisibility(View.GONE);
				sb_voice.setProgress(0);
			}
			setMute(targetValue);
			break;
		case R.id.iv_pre:
			playPre();
			break;
		case R.id.iv_next:
			playNext();
			break;
		case R.id.iv_go_fast:
			fastGoOrBack(true);
			break;
		case R.id.iv_back_fast:
			fastGoOrBack(false);
			break;
		case R.id.searchbut:

			break;
		case R.id.head:
			finish();
			break;
		}
	}

	/**
	 * 停止投屏服务
	 */
	private void stopDLNAService() {
		Intent intent = new Intent(getApplicationContext(), DLNAService.class);
		stopService(intent);
	}

	private class DeviceAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			if (mDevices == null) {
				return 0;
			} else {
				return mDevices.size();
			}
		}

		@Override
		public Object getItem(int position) {
			if (mDevices != null) {
				return mDevices.get(position);
			}

			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = View.inflate(getApplicationContext(),
						R.layout.item_lv_main, null);
				holder = new ViewHolder();
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			holder.tv_name_item = (TextView) convertView
					.findViewById(R.id.tv_name_item);
			holder.tv_name_item.setText(mDevices.get(position)
					.getFriendlyName());
			return convertView;
		}
	}

	static class ViewHolder {
		private TextView tv_name_item;
	}
}
