package fake

import akka.stream.scaladsl.Source
import com.joypeg.scamandrill.client.MandrillAsyncClient
import com.joypeg.scamandrill.models.{MSendMessage, MSendMsg, MTo}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class Sender(cl: MandrillAsyncClient, i: Int)(implicit ec: ExecutionContext) {
  val apiKey = "" // put real key here

  def send() = {
    val messages = for {j <- 0 to 30} yield {
      val mmsg = new MSendMsg(html = s"Hello <b>beauty</b> + <i>${i} : ${j}</i>", text = s"Hello beauty from ${i} msg + ${j}", subject = "Hello beauty!", from_email = "no-reply@mewe.com",
        from_name = "Scamandril test", to = List(MTo("marek+10@mewe.com")))
      MSendMessage(key = apiKey, message = mmsg, async = true)
    }
    val msgSource = Source(messages)
    cl.runMessagesSendSource(msgSource)
  }
}

object SendingTest {
  def main(args: Array[String]): Unit = {
    val scc = new MandrillAsyncClient()
    implicit val mat = scc.materializer
    implicit val ec = scc.system.dispatcher
    //    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))


    val senders = for {i <- 0 to 1} yield new Sender(scc, i)
    val fx = Future.sequence(senders.map(_.send()))

    val res = Await.result(fx, 20 minutes)
    println(s"Result is ${res}")
    scc.shutdownSystem()
    println("Done")

  }

}
