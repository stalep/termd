/*
 * Copyright 2015 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.termd.core.http.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.termd.core.telnet.netty.ReadlineBootstrap;
import io.termd.core.tty.TtyConnection;
import io.termd.core.util.Helper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Convenience class for quickly starting a Netty Tty server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NettyWebsocketBootstrap {

  public static void main(String[] args) throws Exception {
    NettyWebsocketBootstrap bootstrap = new NettyWebsocketBootstrap("localhost", 8080);
    bootstrap.startBlocking(ReadlineBootstrap.READLINE);
    System.in.read();
    bootstrap.stopBlocking();
  }

  private final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
  private final String host;
  private final int port;
  private EventLoopGroup group;
  private Channel channel;

  public NettyWebsocketBootstrap(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public void start(Consumer<TtyConnection> handler, Consumer<Throwable> doneHandler) {
    group = new NioEventLoopGroup();

    ServerBootstrap b = new ServerBootstrap();
    b.group(group)
        .channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new TtyServerInitializer(channelGroup, handler));

    ChannelFuture f = b.bind(host, port);
    f.addListener(abc -> {
      if (abc.isSuccess()) {
        channel = f.channel();
        doneHandler.accept(null);
      } else {
        doneHandler.accept(abc.cause());
      }
    });
  }

  public void startBlocking(Consumer<TtyConnection> handler) throws Exception {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    start(handler, err -> {
      if (err != null) {
        fut.completeExceptionally(err);
      } else {
        fut.complete(null);
      }
    });
    try {
      fut.get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Helper.uncheckedThrow(e.getCause());
    }
  }

  public void stop(Consumer<Void> doneHandler) {
    if (channel != null) {
      channel.close();
    }
    channelGroup.close();
    GenericFutureListener abc = f -> doneHandler.accept(null);
    group.shutdownGracefully().addListener(abc);
  }

  public void stopBlocking() throws InterruptedException {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    stop(fut::complete);
    try {
      fut.get();
    } catch (ExecutionException ignore) {
      // Log me I'm famous
    }
  }
}