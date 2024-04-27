package channel.jettywebsockets

import channel.{ArrayMessageBuffer, InChan, MessageBuffer, OutChan, Prod}
import de.rmgk.delay.{Async, syntax, Callback as DelayCallback}
import org.eclipse.jetty.http.pathmap.PathSpec
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.{Handler, Server, ServerConnector}
import org.eclipse.jetty.util.Callback as JettyUtilCallback
import org.eclipse.jetty.websocket.api.Session.Listener
import org.eclipse.jetty.websocket.api.{Session, Callback as JettyCallback}
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.server
import org.eclipse.jetty.websocket.server.{ServerUpgradeResponse, ServerWebSocketContainer, WebSocketCreator, WebSocketUpgradeHandler}

import java.net.URI
import java.nio.ByteBuffer
import scala.util.{Failure, Success}

object JettyWsListener {

  def startListen(port: Int, pathSpec: PathSpec) = {
    val server = new Server()

    val connector = new ServerConnector(server)
    connector.setPort(port)
    server.addConnector(connector)

    fromServer(server, pathSpec)

  }

  def fromServer(server: Server, pathSpec: PathSpec) =
    new JettyWsListener(server, pathSpec)

  def webSocketCreator(delayCallback: DelayCallback[JettyWsConnection]) =
    new WebSocketCreator {
      override def createWebSocket(
          request: server.ServerUpgradeRequest,
          upgradeResponse: ServerUpgradeResponse,
          callback: JettyUtilCallback
      ): AnyRef = {
        println(s"creating ws handler")
        new JettyWsHandler(delayCallback)
      }
    }

}

class JettyWsListener(val server: Server, val pathSpec: PathSpec) {

  def connections(moreHandler: Option[Handler] = None): Async[Any, JettyWsConnection] = Async.fromCallback {

    val context = new ContextHandler()
    server.setHandler:
      moreHandler match
        case None => context
        case Some(more) => Handler.Sequence(context, more)
    val webSocketHandler = WebSocketUpgradeHandler.from(
      server,
      context,
      (wsContainer: ServerWebSocketContainer) => {
        println(s"adding mapping")
        wsContainer.addMapping(
          pathSpec,
          JettyWsListener.webSocketCreator(Async.handler)
        )
      }
    )
    context.setHandler(webSocketHandler)

    println(s"server bound")

  }

}

object JettyWsConnection {

  def connect(uri: URI): Async[Any, JettyWsConnection] = Async.fromCallback {

    val client = new WebSocketClient()
    client.start()
    // this returns a future
    println(s"client started")
    client.connect(new JettyWsHandler(Async.handler), uri).toAsync.run: res =>
      println(s"client connected")
      println(res)
    ()
  }
}

class JettyWsConnection(handler: JettyWsHandler) extends InChan with OutChan {

  private[jettywebsockets] var internalCallback: DelayCallback[MessageBuffer] = scala.compiletime.uninitialized

  override def receive: Prod[MessageBuffer] = Async.fromCallback {
    handler.getSession.demand()
    internalCallback = Async.handler
  }
  override def send(data: MessageBuffer): Async[Any, Unit] = Async.fromCallback {

    handler.getSession.sendBinary(
      ByteBuffer.wrap(data.asArray),
      new JettyCallback {
        override def succeed(): Unit          = Async.handler.succeed(())
        override def fail(x: Throwable): Unit = Async.handler.fail(x)
      }
    )
  }
}

class JettyWsHandler(connectionEstablished: DelayCallback[JettyWsConnection])
    extends Listener.Abstract {

  val connectionApi: JettyWsConnection = JettyWsConnection(this)

  override def onWebSocketOpen(session: Session): Unit = {
    super.onWebSocketOpen(session)
    connectionEstablished.succeed(connectionApi)
  }

  override def onWebSocketBinary(buffer: ByteBuffer, callback: JettyCallback): Unit = {

    val data = new Array[Byte](buffer.remaining())
    buffer.get(data)

    connectionApi.internalCallback.succeed(ArrayMessageBuffer(data))

    callback.succeed()
    getSession.demand()
  }

  override def onWebSocketText(message: String): Unit = {
    getSession.demand()
  }

  override def onWebSocketClose(statusCode: Int, reason: String): Unit = {
    println(s"closing message because $reason")
  }

  override def onWebSocketError(cause: Throwable): Unit = {
    println(s"received explicit error $cause")
    connectionApi.internalCallback.fail(cause)
  }

}
