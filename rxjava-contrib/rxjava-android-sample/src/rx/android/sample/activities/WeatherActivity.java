package rx.android.sample.activities;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit.RestAdapter;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.concurrency.AndroidSchedulers;
import rx.android.sample.R;
import rx.android.sample.model.Observers;
import rx.android.sample.model.WeatherData;
import rx.android.sample.util.ThreadUtils;
import rx.concurrency.Schedulers;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;
import android.app.ListActivity;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class WeatherActivity extends ListActivity {
	private static final String TAG = "WeatherActivity";

	private final Map<String, WeatherData> weather = new LinkedHashMap<String, WeatherData>();

	private ListView listView;
	private WeatherAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
    	Log.v(TAG, "Num threads: " + Schedulers.threadPoolForIO().degreeOfParallelism());

		listView = new ListView(this);
		listView.setId(android.R.id.list);

		adapter = new WeatherAdapter();
		setListAdapter(adapter);

		setContentView(listView);
		getWeatherForCities(Arrays.asList(
				"San Francisco", "Sydney", "London", "foobar"));
	}

	private void getWeatherForCities(List<String> cities) {
		Observable<WeatherData> obs = Observable.from(cities)
		        .mapMany(new Func1<String, Observable<WeatherData>>() {
		            @Override
		            public Observable<WeatherData> call(String s) {
		                return ApiManager.getWeatherData(s);
		            }
		        })
		        .observeOn(AndroidSchedulers.mainThread());

		obs.subscribe(new Observers.LoggingObserver<WeatherData>(TAG) {
			@Override
			public void onNext(WeatherData data) {
				super.onNext(data);
				weather.put(data.name, data);
				adapter.notifyDataSetChanged();
			}
		});
	}

	private class WeatherAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return weather.size();
		}

		@Override
		public WeatherData getItem(int position) {
			return weather.get(weather.keySet().toArray()[position]);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView tv = (TextView) convertView;
			if (tv == null) {
				tv = new TextView(WeatherActivity.this);
				Resources r = getResources();
				int p = (int)r.getDimension(R.dimen.content_padding);
				tv.setPadding(p, p, p, p);
				tv.setTextSize(TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_DIP,
						16,
						r.getDisplayMetrics()));

				convertView = tv;
			}

			tv.setText(getItem(position).toString());
			return tv;
		}
	}

	public static class ApiManager {

	    private interface ApiManagerService {
	        @GET("/weather")
	        WeatherData getWeather(@Query("q") String place, @Query("units") String units);
	    }

	    private static final RestAdapter restAdapter = new RestAdapter.Builder()
	        .setServer("http://api.openweathermap.org/data/2.5")
	        .build();

	    private static final ApiManagerService apiManager = restAdapter.create(ApiManagerService.class);

	    public static Observable<WeatherData> getWeatherData(final String city) {
	        return Observable.create(new Observable.OnSubscribeFunc<WeatherData>() {
	            @Override
	            public Subscription onSubscribe(Observer<? super WeatherData> observer) {
	            	ThreadUtils.assertNotOnMain();
	                try {
	                	Log.v(TAG, "I'm sleepy... " + Thread.currentThread().getName());
	                	Thread.sleep(5000);
	                	Log.v(TAG, "Making remote call...");
	                    observer.onNext(apiManager.getWeather(city, "metric"));
	                	Log.v(TAG, "Request completed");
	                    observer.onCompleted();
	                }
	                catch (Exception e) {
	                    observer.onError(e);
	                }

	                return Subscriptions.empty();
	            }
	        }).subscribeOn(Schedulers.threadPoolForIO());
	    }
	}
}
