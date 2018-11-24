package com.thenewmotion.ocpp
package akkahttp

import akka.http.scaladsl.model._

import scala.xml.NodeSeq

/**
 * @author Yaroslav Klymko
 */
object SoapResponse {
  val contentType = MediaTypes.`application/soap+xml` withCharset HttpCharsets.`UTF-8`

  def apply(xml: NodeSeq) = HttpResponse(
    entity = HttpEntity(contentType, xml.toString()))
}
