package com.maxk;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
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
		
		queueFetchTask(newFetchTask(url));
	}
	
	/**
	 * Creates a concrete ResourceFetchTask object, which carries out the actual fetching
	 * @param url
	 * @return
	 */
	protected ResourceFetchTask newFetchTask(final String url)
	{
		ResourceFetchTask task = new ResourceFetchTask(url) {
			@Override
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
		
		return task;
	}
	
	private void queueFetchTask(final ResourceFetchTask task)
	{
		if (task == null) throw new IllegalArgumentException("task");
		
		FutureTask<?> fetch = new ResourceFetchFutureTask(task);
		
		try
		{
			_executor.execute(fetch);
			_fetches.put(task.getUrl(), fetch);
		}
		catch (Exception ex)
		{
			if (_callback != null) _callback.onFetchResourceFailed(task.getUrl());
		}
	}
	
	private void completeResourceFetch(ResourceFetchFutureTask fetch)
	{
		_fetches.remove(fetch.getTask().getUrl());
		
		notifyFetchFinished(fetch);
		notifyAllFetchesFinished();
	}
	
	private void notifyFetchFinished(ResourceFetchFutureTask fetch)
	{
		if (fetch.isCancelled()) return;
		
		ResourceFetcherCallback callback = _callback;
		
		if (callback != null)
		{
			try
			{
				callback.onFetchedResource(fetch.getTask().getUrl(), fetch.get());
			}
			catch (Exception ex)
			{
				callback.onFetchResourceFailed(fetch.getTask().getUrl());
			}
		}
	}
	
	private void notifyAllFetchesFinished()
	{
		if (isFetching()) return;
		
		ResourceFetcherCallback callback = _callback;
		if (callback != null)
		{
			callback.onFetchedAllResources();
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
	
	/**
	 * Cancels all queued and executing fetches
	 */
	public synchronized void cancelAll()
	{
		if (_fetches.isEmpty()) return;
		
		for (Entry<String, Future<?>> entry : _fetches.entrySet())
		{
			entry.getValue().cancel(true);
		}
		
		_fetches.clear();
	}
	
	public void setCallback(ResourceFetcherCallback callback)
	{
		_callback = callback;
	}
	
	/**
	 * Manages the life cycle of the resource fetching task (which is encapsulated in the ResourceFetchTask class)
	 * @author MaxK
	 *
	 */
	private class ResourceFetchFutureTask extends FutureTask<InputStream>
	{
		private final ResourceFetchTask _task;
		
		public ResourceFetchFutureTask(ResourceFetchTask task)
		{
			super(task);
			
			if (task == null) throw new IllegalArgumentException("task");
			
			_task = task;
		}
		
		public ResourceFetchTask getTask()
		{
			return _task;
		}
		
		@Override
		public boolean cancel(boolean mayInterruptIfRunning)
		{
			_task.cancel();
			return super.cancel(mayInterruptIfRunning);
		}
		
		@Override
		protected void done()
		{
			super.done();
			
			completeResourceFetch(this);
		}
	}
}
