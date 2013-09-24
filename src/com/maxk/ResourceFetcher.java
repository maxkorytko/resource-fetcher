package com.maxk;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Fetches resources from the web
 * All methods are non-blocking
 * 
 * @author MaxK
 * 
 */
public class ResourceFetcher
{
	public static interface ResourceFetcherCallback
	{
		void onFetchedResource(String url, InputStream data);
		void onFetchResourceFailed(String url);
		void onFetchedAllResources();
	}
	
	private final ExecutorService _executor;
	private final Map<String, Future<?>> _fetches;
	protected ResourceFetcherCallback _callback;
	
	public ResourceFetcher(ExecutorService executor)
	{
		if (executor == null) throw new IllegalArgumentException("executor");
		
		_executor = executor;
		_fetches = new ConcurrentHashMap<String, Future<?>>();
	}
	
	/**
	 * Fetches the data from the provided URL
	 * @param url
	 */
	public void queueFetchFromUrl(String url)
	{
		if (url == null) return;
		if (_fetches.containsKey(url)) return;
		
		queueFetch(createFetch(url));
	}
	
	private FetchTask createFetch(String url)
	{
		FetchTask fetch = newFetchFor(url);
		
		if (fetch != null)
		{
			fetch.setCallback(_callback);
		}
		
		return fetch;
	}
	
	/**
	 * Creates a concrete FetchTask object, which carries out the actual fetching
	 * @param url
	 * @return
	 */
	protected FetchTask newFetchFor(final String url)
	{
		FetchTask fetch = new FetchTask(url) {
			public InputStream getResource(String url) throws Exception
			{
				URL resourceUrl = new URL(url);
				HttpURLConnection connection = (HttpURLConnection)resourceUrl.openConnection();
				
				connection.setConnectTimeout(10000);
				connection.setRequestMethod("GET");
				connection.setDoInput(true);
				
				connection.connect();
				
				int responseCode = connection.getResponseCode();
				if (responseCode != 200)
				{
					throw new Exception(String.format("GET %s failed with %d response", url, responseCode));
				}
						
				return connection.getInputStream();
			}
		};
		
		return fetch;
	}
	
	private void queueFetch(final FetchTask fetch)
	{
		if (fetch == null) throw new IllegalArgumentException("fetch");
		
		FutureTask<InputStream> task = new FutureTask<InputStream>(fetch) {
			@Override
			public boolean cancel (boolean mayInterruptIfRunning)
			{
				fetch.cancel();
				return super.cancel(mayInterruptIfRunning);
			}
			
			@Override
			protected void done()
			{
				super.done();
				
				_fetches.remove(fetch._url);
				if (!isFetching()) notifyFetchedAll();
			}
		};
		
		try
		{
			_executor.execute(task);
			_fetches.put(fetch._url, task);
		}
		catch (Exception ex)
		{
			if (_callback != null) _callback.onFetchResourceFailed(fetch._url);
		}
	}
	
	public boolean isFetching()
	{
		return getFetchCount() > 0;
	}
	
	/**
	 * Returns the number of fetches queued (including currently executing ones)
	 * @return
	 */
	protected int getFetchCount()
	{
		return _fetches.size();
	}
	
	private void notifyFetchedAll()
	{
		ResourceFetcherCallback callback = _callback;
		if (callback != null)
		{
			callback.onFetchedAllResources();
		}
	}
	
	/**
	 * Cancels all queued and executing fetches
	 */
	public synchronized void cancelAll()
	{
		if (_fetches.isEmpty()) return;
		
		for (Entry<String, Future<?>> entry : _fetches.entrySet())
		{
			entry.getValue().cancel(false);
		}
		
		_fetches.clear();
	}
	
	public void setCallback(ResourceFetcherCallback callback)
	{
		_callback = callback;
	}
	
	/**
	 * A callable task for fetching a resource from a URL
	 * Supports cancellation
	 * @author MaxK
	 *
	 */
	public abstract class FetchTask implements Callable<InputStream>
	{
		private final String _url;
		private volatile boolean _isCancelled;
		private ResourceFetcherCallback _callback;
		
		public FetchTask(String url)
		{
			if (url == null) throw new IllegalArgumentException("url");
			
			_url = url;
		}
		
		/**
		 * Cancels the task
		 */
		public void cancel()
		{
			_isCancelled = true;
		}
		
		/**
		 * Returns true if the task has been cancelled
		 * @return
		 */
		public boolean isCancelled()
		{
			return _isCancelled;
		}
		
		@Override
		public final InputStream call()
		{
			InputStream resource = null;
			
			try
			{
				if (!isCancelled())
				{
					resource = getResource(_url);
					if (!isCancelled()) notifyFetchFinished(resource);
				}
			}
			catch (Exception ex)
			{
				notifyFetchFailed();
			}
			
			return resource;
		}
		
		/**
		 * Obtains a resource from the given URL
		 * @param url
		 * @return InputStream of the resource
		 * @throws Exception when resource fetching fails
		 */
		protected abstract InputStream getResource(String url) throws Exception;
		
		private void notifyFetchFinished(InputStream data)
		{
			if (_callback != null) _callback.onFetchedResource(_url, data);
		}
		
		private void notifyFetchFailed()
		{
			if (_callback != null) _callback.onFetchResourceFailed(_url);
		}
		
		private void setCallback(ResourceFetcherCallback callback)
		{
			_callback = callback;
		}
	}
}
