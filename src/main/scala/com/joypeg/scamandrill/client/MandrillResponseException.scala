package com.joypeg.scamandrill.client

import akka.stream.Materializer
import spray.json._

import scala.concurrent.{ExecutionContext, Future}


case class MandrillError(status: String, code: Int, name: String, message: String)

case class MandrillResponseException(httpCode: Int,
                                     httpReason: String,
                                     mandrillError: MandrillError) extends RuntimeException {}

object MandrillResponseExceptionJsonProtocol extends DefaultJsonProtocol {
  implicit val MandrillErrorj = jsonFormat4(MandrillError)
}

object MandrillResponseException {

  def apply(ex: UnsuccessfulResponseException)(implicit mat: Materializer, ec: ExecutionContext): Future[MandrillResponseException] = {
    import MandrillResponseExceptionJsonProtocol._
    val me = ex.msg.parseJson.convertTo[MandrillError]
    Future.successful(new MandrillResponseException(
      ex.status,
      ex.reason,
      me
    )
    )
  }
}
