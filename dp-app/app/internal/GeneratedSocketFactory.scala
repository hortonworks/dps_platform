package internal

import java.net.{InetAddress, Socket}
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

import com.typesafe.scalalogging.Logger


class GeneratedSocketFactory(tms: SSLSocketFactory) extends SSLSocketFactory {
  private val logger = Logger(classOf[GeneratedSocketFactory])

  override def getDefaultCipherSuites = GeneratedSocketFactory.sfWrapper.get.getDefaultCipherSuites()

  override def createSocket(socket: Socket, host: String, port: Int, bln: Boolean) = GeneratedSocketFactory.sfWrapper.get.createSocket(socket, host, port, bln)

  override def getSupportedCipherSuites = GeneratedSocketFactory.sfWrapper.get.getSupportedCipherSuites()

  override def createSocket(host: String, port: Int) = GeneratedSocketFactory.sfWrapper.get.createSocket(host, port)

  override def createSocket(host: String, port: Int, inetAddress: InetAddress, port1: Int) = GeneratedSocketFactory.sfWrapper.get.createSocket(host, port, inetAddress, port1)

  override def createSocket(inetAddress: InetAddress, port: Int) = GeneratedSocketFactory.sfWrapper.get.createSocket(inetAddress, port)

  override def createSocket(inetAddress: InetAddress, port: Int, inetAddress1: InetAddress, port1: Int) = GeneratedSocketFactory.sfWrapper.get.createSocket(inetAddress, port, inetAddress1, port1)
}

object GeneratedSocketFactory {

  val sfWrapper: AtomicReference[SSLSocketFactory] = new AtomicReference[SSLSocketFactory]()

  def getDefault(): SocketFactory = new GeneratedSocketFactory(sfWrapper.get())

  def set(sf: SSLSocketFactory) = sfWrapper.set(sf)

}

