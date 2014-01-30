package com.maxk;

import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * A callable task for fetching a resource from a URL
 * Supports cancellation
 *
 * @author MaxK
 *
*/
public abstract class ResourceFetchTask implements Callable<InputStream>
{
	private final String _url;
	private volatile boolean _isCancelled;
	
	public ResourceFetchTask(String url)
	{
		if (url == null) throw new IllegalArgumentException("url");
		
		_url = url;
	}
	
	@Override
	public InputStream call() throws Exception
	{
		return isCancelled() ? null : getResource(_url);
	}
	
	/**
	 * Returns true if the task has been cancelled
	 * @return
	 */
	public boolean isCancelled()
	{
		return _isCancelled;
	}
	
	/**
	 * Obtains a resource from the given URL
	 * @param url
	 * @return InputStream of the resource
	 * @throws Exception when resource fetching fails
	 */
	protected abstract InputStream getResource(String url) throws Exception;
	
	/**
	 * Cancels the task provided that it has not started yet
	 */
	public void cancel()
	{
		_isCancelled = true;
	}
	
	public String getUrl()
	{
		return _url;
	}
}
