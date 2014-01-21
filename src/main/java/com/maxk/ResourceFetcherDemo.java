package com.maxk;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.maxk.ResourceFetcher.ResourceFetcherCallback;

public class ResourceFetcherDemo implements ResourceFetcherCallback
{
	private final ExecutorService _executor = Executors.newCachedThreadPool();
	private final ResourceFetcher _resourceFetcher = new ResourceFetcher(_executor);
	
	public static void main(String[] args)
	{
		new ResourceFetcherDemo().start();
	}
	
	private void start()
	{
		fetchResources();
	}
	
	private void fetchResources()
	{
		_resourceFetcher.setCallback(this);
		
		_resourceFetcher.queueFetchFromUrl("http://www.mri.gov.on.ca/obr/wp-content/uploads/toronto-01.jpg");
		_resourceFetcher.queueFetchFromUrl("http://upload.wikimedia.org/wikipedia/commons/1/13/Toronto_at_Dusk_-a.jpg");
		_resourceFetcher.queueFetchFromUrl("http://1.bp.blogspot.com/-NSGv7uw_aoQ/TViTblNKIAI/AAAAAAAAGZw/zzu4Up8XHJA/s1600/yongeDSC_8105.jpg");
	}

	@Override
	public void onFetchedResource(String url, InputStream data)
	{
		System.out.println("Resource fetched: " + url);
		
		// TODO: save data
	}

	@Override
	public void onFetchResourceFailed(String url)
	{
		System.out.println("Failed to fetch the resource: " + url);
	}

	@Override
	public void onFetchedAllResources()
	{
		System.out.println("Done fetching");
		
		_executor.shutdown();
	}
}
