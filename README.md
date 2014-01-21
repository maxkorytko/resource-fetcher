Resource-Fetcher
================

A simple project implementing a resource fetcher

Resource fetcher allows you to download data from a URL asynchronously.

Resource fetcher relies on java.util.concurrent.ExecutorService to do the job. It means that you are free to define your
execution policy by providing an ExecutorService tuned to your needs.

Sample usage:

ResourceFetcher fetcher = new ResourceFetcher(Executores.newCachedThreadPool());
fetcher.setCallback(this); // the callback will be notified when the resource is finished fetching

fetcher.quueFetchFromUrl("http://www.apple.com");
fetcher.queueFetchFromUrl("http://yourhost.com/path/to/image.png");

Execute gradle run to build and run the demo app
Execute gradle build to build and run tests