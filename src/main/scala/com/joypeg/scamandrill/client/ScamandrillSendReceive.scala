package com.joypeg.scamandrill.client

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.joypeg.scamandrill.utils.SimpleLogger

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class StreamResults(success: Int, failure: Int)

sealed trait ScOpResult

case object ScOK extends ScOpResult

case object ScFail extends ScOpResult

/**
  * This trait abstract on top of spray the handling of all request / response to the mandrill API. Its
  * executeQuery fuction is the one use by both the async client and the blocking one (wrapper).
  */
trait ScamandrillSendReceive extends SimpleLogger {

  type Entity = Either[Throwable, RequestEntity]

  implicit val system: ActorSystem
  implicit val materializer = ActorMaterializer()
  val veryLongTimeout = 15 minutes

  import system.dispatcher

  val clientFlow = Http().cachedHostConnectionPoolHttps[Int]("mandrillapp.com")
  val queueFlow = Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]]("mandrillapp.com")

  val queue = Source.queue[(HttpRequest, Promise[HttpResponse])](1024, OverflowStrategy.dropNew)
    .via(queueFlow)
    .toMat(Sink.foreach({
      case ((Success(resp), p)) => p.success(resp)
      case ((Failure(e), p)) => p.failure(e)
    }))(Keep.left)
    .run

  /**
    * Fire a request to Mandrill API and try to parse the response. Because it return a Future[S], the
    * unmarshalling of the response body is done via a partially applied function on the Future transformation.
    * Uses spray-can internally to fire the request and unmarshall the response, with spray-json to do that.
    *
    * @param endpoint - the Mandrill API endpoint for the operation, for example '/messages/send.json'
    * @param reqBody  - the body of the post request already marshalled as json
    * @param handler  - this is the unmarshaller fuction of the response body, partially applied function
    * @tparam S - the type of the expected body response once unmarshalled
    * @return - a future of the expected type S
    * @note as from the api documentation, all requests are POST, and You can consider any non-200 HTTP
    *       response code an error - the returned data will contain more detailed information
    */
  def executeQuery[S](endpoint: String, reqBodyF: Future[RequestEntity])(handler: (HttpResponse => Future[S])): Future[S] = {

    //request handler
    def rh(rsp: HttpResponse): Future[S] = {
      if (rsp.status.isSuccess()) handler(rsp)
      else {
        Unmarshal(rsp.entity).to[String].flatMap(msg =>
          Future.failed(new UnsuccessfulResponseException(rsp.status.intValue(), rsp.status.reason(), msg))
        )
      }
    }

    //TODO: reqbody <: MandrillResponse and S :< MandrillRequest
    reqBodyF.flatMap { reqBody =>
      val request = HttpRequest(method = HttpMethods.POST, uri = Uri("/api/1.0" + endpoint), entity = reqBody)
      val promise = Promise[HttpResponse]
      val r = request -> promise
      //val timeout = java.util.concurrent.TimeUnit
      queue.offer(r) flatMap {
        _ match {
          case Enqueued => promise.future
          case _ => Future.failed(new RuntimeException("request not enqueued"))
        }
      } flatMap (rh)
    }
  }

  def rhTry[S](t: (Try[HttpResponse], Int))(implicit ec: ExecutionContext): Future[ScOpResult] = {
    t._1 map { resp =>
      resp.entity.dataBytes.runWith(Sink.ignore).map { _ =>
        if (resp.status.isSuccess()) ScOK
        else ScFail
      }
    } getOrElse (Future.successful(ScFail))
  }

  def toRequest(endpoint: String)(reqBody: MessageEntity): (HttpRequest, Int) =
    (HttpRequest(method = HttpMethods.POST, uri = Uri("/api/1.0" + endpoint), entity = reqBody), 1)

  def addScResult(s: StreamResults, e: ScOpResult): StreamResults = {
    e match {
      case ScOK => s.copy(success = s.success + 1)
      case ScFail => s.copy(failure = s.failure + 1)
    }
  }

  def runSource[T, S](endpoint: String, src: Source[T, _], m: T => Future[MessageEntity]): Future[StreamResults] = {
    val toReq = toRequest(endpoint)(_)
    src.mapAsync(1)(m).map(toReq).via(clientFlow).mapAsync(1)(rhTry).runFold(StreamResults(0, 0))(addScResult)
  }


  /**
    * Asks all the underlying actors to close (waiting for 1 second)
    * and then shut down the system. Because the blocking client is
    * basically a wrapper of the async one, both the async and blocking
    * client are supposed to call this method when they are not required
    * or the application using them exit.
    */
  def shutdown(): Unit = {
    logger.info("asking all actor to close")
    //queue.complete()
    materializer.shutdown()
    Await.ready(Http().shutdownAllConnectionPools(), 1 second)
    Await.ready(system.terminate(), 1 second)
    logger.info("actor system shut down")
  }
}
