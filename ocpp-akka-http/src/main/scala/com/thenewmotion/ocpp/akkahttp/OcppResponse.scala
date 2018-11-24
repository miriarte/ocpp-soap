package com.thenewmotion.ocpp
package akkahttp

import akka.http.scaladsl.model.HttpResponse
import soapenvelope12.{Body, Fault}
import soap.{ReachEnvelope, ReachFault}
/**
 * @author Yaroslav Klymko
 */
object OcppResponse {
  def apply(fault: Fault): HttpResponse = apply(fault.asBody)

  def apply(body: => Body): HttpResponse = {
    val env = soapenvelope12.Envelope(None, body, Map())
    SoapResponse(env.toXml)
  }
}
