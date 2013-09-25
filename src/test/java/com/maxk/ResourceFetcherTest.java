package com.maxk;

import java.io.InputStream;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import com.maxk.ResourceFetcher;
import com.maxk.ResourceFetcher.ResourceFetcherCallback;

public class ResourceFetcherTest extends TestCase
{
	private final ExecutorService _executor = Executors.newCachedThreadPool();
	
	public static class MockResourceFetcher extends ResourceFetcher
	{
		private final CountDownLatch _startSignal = new CountDownLatch(1);
		private CountDownLatch _finishedSignal;
		
		private final List<String> _fetchedUrls = new Vector<String>();
		private final List<String> _cancelledUrls = new Vector<String>();
		
		public MockResourceFetcher(ExecutorService executor)
		{
			super(executor);
		}
		
		@Override
		protected FetchTask newFetchFor(final String url)
		{
			return new FetchTask(url) {
				@Override
				public InputStream getResource(String url)
				{
					try
					{
						_startSignal.await();
					}
					catch (InterruptedException ex) { }
					
					boolean shouldFail = url.contains("Fail");
					
					if (!shouldFail)
					{
						if (isCancelled())
						{
							_cancelledUrls.add(url);
						}
						else
						{
							_fetchedUrls.add(url);
						}
					}
					
					_finishedSignal.countDown();
					
					if (shouldFail) throw new RuntimeException();
					
					return null;
				}
			};
		}
		
		public void fetchAndAwait() throws InterruptedException
		{
			_finishedSignal = new CountDownLatch(getFetchCount());
			
			_startSignal.countDown();
			_finishedSignal.await(2L, TimeUnit.SECONDS);
			
			// give the fetcher a little bit of time to remove the last fetch from the executor
			Thread.sleep(1000);
		}
		
		public void cancelAllAndAwait() throws InterruptedException
		{
			_finishedSignal = new CountDownLatch(getFetchCount());
			
			cancelAll();
			_startSignal.countDown();
			
			_finishedSignal.await(2L, TimeUnit.SECONDS);
		}
		
		public List<String> getFetchedUrls()
		{
			return _fetchedUrls;
		}
		
		public List<String> getCancelledUrls()
		{
			return _cancelledUrls;
		}
	}
	
	public void testQeueFetchFromUrl() throws Exception
	{
		MockResourceFetcher fetcher = new MockResourceFetcher(_executor);
		
		final String url = "http://someUrl";
		final String otherUrl = "http://someOtherUrl";
		
		fetcher.queueFetchFromUrl(url);
		fetcher.queueFetchFromUrl(otherUrl);
		fetcher.queueFetchFromUrl("http://yetAnotherUrl");
		
		assertTrue("Fetcher must start fetching", fetcher.isFetching());
		
		fetcher.fetchAndAwait();
		
		assertFalse("Fetcher must be done fetching by now", fetcher.isFetching());
		assertTrue(fetcher.getFetchedUrls().contains(url));
		assertTrue(fetcher.getFetchedUrls().contains(otherUrl));
		assertEquals("Fetcher must fetch all urls", 3, fetcher.getFetchedUrls().size());
	}
	
	public void testCancelAll() throws Exception
	{
		MockResourceFetcher fetcher = new MockResourceFetcher(_executor);
		
		fetcher.queueFetchFromUrl("http://someUrl");
		fetcher.queueFetchFromUrl("http://someOtherUrl");
		
		// pause this thread to ensure the fetcher has enough time to dequeue all test fetches
		// otherwise, some fetches may not start executing
		//
		Thread.sleep(500);
		
		fetcher.cancelAllAndAwait();
		
		assertEquals("Fetcher must stop fetching when cancelled", false, fetcher.isFetching());
		assertEquals(0, fetcher.getFetchedUrls().size());
		assertEquals(2, fetcher.getCancelledUrls().size());
	}
	
	public void testCallback() throws Exception
	{
		final AtomicInteger fetchesCounter = new AtomicInteger(0);
		final AtomicInteger allFetchesCounter = new AtomicInteger(0);
		
		MockResourceFetcher fetcher = new MockResourceFetcher(_executor);
		fetcher.setCallback(new ResourceFetcherCallback() {
			@Override
			public void onFetchedResource(String url, InputStream data)
			{
				fetchesCounter.incrementAndGet();
			}

			@Override
			public void onFetchResourceFailed(String url)
			{
				fetchesCounter.incrementAndGet();
			}

			@Override
			public void onFetchedAllResources()
			{	
				allFetchesCounter.incrementAndGet();
			}
		});
		
		fetcher.queueFetchFromUrl("A");
		fetcher.queueFetchFromUrl("B");
		fetcher.queueFetchFromUrl("C");
		fetcher.queueFetchFromUrl("Fail");
		
		fetcher.fetchAndAwait();
		
		assertEquals("Not all resources have been fetched", 4, fetchesCounter.get());
		assertEquals("All resources must be fetched exactly once", 1, allFetchesCounter.get());
	}
}