Resource-Fetcher
================

A simple Java implementation of fetching files from the web asynchronously.

Resource fetcher allows you to download data from a URL asynchronously.

Resource fetcher relies on java.util.concurrent.ExecutorService to do the job. It means that you are free to define your
execution policy by providing an ExecutorService tuned to your needs.

Sample usage:

ResourceFetcher fetcher = new ResourceFetcher(Executors.newCachedThreadPool());

fetcher.setCallback(this);

fetcher.queueFetchFromUrl("http://www.apple.com");
fetcher.queueFetchFromUrl("http://yourhost.com/path/to/image.png");

How to build and run
====================

Requires Java SE 5 to build.

Execute gradle run to build and run the demo app.

Execute gradle build to build and run tests.
