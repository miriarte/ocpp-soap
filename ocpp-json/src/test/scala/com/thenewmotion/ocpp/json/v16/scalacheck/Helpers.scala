package com.thenewmotion.ocpp
package json.v16
package scalacheck

import java.time.{ZonedDateTime, Instant, ZoneId}
import org.scalacheck.Gen, Gen._
import enums.reflection.EnumUtils.{Enumerable, Nameable}
import java.net.URI

object Helpers {

  def transactionIdGen: Gen[Int] = chooseNum(1, 4000)
  def stopReasonGen: Gen[Option[String]] = enumerableWithDefaultNameGen(messages.StopReason)

  // currently None goes to Some(List()) after two-way conversion
  def txnDataGen: Gen[Option[List[Meter]]] = some(listOf(meterGen))

  def connectorIdGen: Gen[Int] = chooseNum(1, 4)
  def connectorIdIncludingChargePointGen: Gen[Int] = chooseNum(0, 4)
  def idTagGen: Gen[String] = alphaNumStr.filter(_.nonEmpty)

  def idTagInfoGen: Gen[IdTagInfo] =
    for {
      status <- enumerableNameGen(messages.AuthorizationStatus)
      expiryDate <- option(dateTimeGen)
      parentIdTag <- option(idTagGen)
    } yield IdTagInfo(status, expiryDate, parentIdTag)

  def meterStartGen: Gen[Int] = chooseNum(0, 6000000)
  def meterStopGen: Gen[Int] = chooseNum(0, 6000000)
  def reservationIdGen: Gen[Int] = choose(0, 100)

  def acceptanceGen: Gen[String] = oneOf(const("Accepted"), const("Rejected"))

  def dateTimeGen: Gen[ZonedDateTime] =
    for {
      randomInstantMillis <- chooseNum(1, Integer.MAX_VALUE.toLong)
    } yield {
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(randomInstantMillis), ZoneId.of("UTC"))
    }

  def meterGen: Gen[Meter] = for {
    timestamp <- dateTimeGen
    sampledValue <- listOf(meterValueGen)
  } yield Meter(ZonedDateTime.now, sampledValue)

  def meterValueGen: Gen[MeterValue] = for {
    value <- alphaNumStr
    context <- enumerableWithDefaultNameGen(messages.Meter.ReadingContext)
    // Hmm, generating these creates freak java.lang.InternalError
    format <- const(None) // enumerableNameGenWithDefault(messages.Meter.ValueFormat, messages.Meter.ValueFormat.Raw)
    measurand <- enumerableWithDefaultNameGen(messages.Meter.Measurand)
    // Hmm, generating these creates freak java.lang.InternalError
    phase <- const(None) // option(enumerableNameGen(messages.Meter.Phase))
    // Hmm, generating these creates freak java.lang.InternalError
    location <- const(None) // enumerableNameGenWithDefault(messages.Meter.Location, messages.Meter.Location.Outlet)
    unit <- enumerableWithDefaultNameGen(messages.Meter.UnitOfMeasure)
  } yield MeterValue(value, context, format, measurand, phase, location, unit)


  def uriGen: Gen[String] = for {
    scheme <- alphaStr.filter(_.nonEmpty)
    host <- alphaNumStr.filter(_.nonEmpty)
    path <- listOf(alphaNumStr).map(elems => "/" + elems.mkString("/"))
    fragment <- alphaNumStr
  } yield new URI(scheme, host, path, fragment).toString

  def chargePointStatusGen: Gen[String] =
    oneOf(
      "Available", "Preparing", "Charging", "SuspendedEV", "SuspendedEVSE",
      "Finishing", "Unavailable", "Reserved", "Faulted"
    )

  def rateLimitGen: Gen[Float] = chooseNum(0, 32).map(x => (x * 10).toFloat / 10)

  def chargingProfileIdGen: Gen[Int] = choose(1, 32500)

  def chargingProfilePurposeGen: Gen[String] = enumerableNameGen(messages.ChargingProfilePurpose)

  def stackLevelGen: Gen[Int] = choose(1, 10)

  def chargingRateUnitGen: Gen[String] = enumerableNameGen(messages.UnitOfChargeRate)

  def chargingSchedulePeriodGen: Gen[ChargingSchedulePeriod] =
    for {
      startPeriod <- chooseNum(1, 4000000)
      limit <- rateLimitGen
      numberPhases <- option(oneOf(1, 2, 3))
    } yield ChargingSchedulePeriod(startPeriod, limit, numberPhases)

  def chargingScheduleGen: Gen[ChargingSchedule] =
    for {
      chargingRateUnit <- chargingRateUnitGen
      chargingSchedulePeriod <- listOf(chargingSchedulePeriodGen)
      duration <- option(chooseNum(1, 4000000))
      startSchedule <- option(dateTimeGen)
      minChargingRate <- option(rateLimitGen)
    } yield ChargingSchedule(chargingRateUnit, chargingSchedulePeriod, duration, startSchedule,minChargingRate)

  def chargingProfileGen: Gen[ChargingProfile] =
    for {
      id <- chargingProfileIdGen
      stackLevel <- stackLevelGen
      purpose <- chargingProfilePurposeGen
      kind <- oneOf("Relative", "Absolute", "Recurring")
      schedule <- chargingScheduleGen
      transactionId <- option(transactionIdGen)
      recurrencyKind <- if (kind == "Recurring") some(oneOf("Daily", "Weekly")) else const(None)
      validFrom <- option(dateTimeGen)
      validTo <- option(dateTimeGen)
    } yield ChargingProfile(id, stackLevel, purpose, kind, schedule, transactionId, recurrencyKind, validFrom, validTo)

  def configurationEntryGen: Gen[ConfigurationEntry] =
    for {
      key <- alphaNumStr
      readOnly <- oneOf(true, false)
      value <- option(words)
    } yield ConfigurationEntry(key, readOnly, value)

  def enumerableGen[T <: Nameable](e: Enumerable[T]): Gen[T]  =
    oneOf(e.values.toList)

  def enumerableNameGen[T <: Nameable](e: Enumerable[T]): Gen[String] = enumerableGen(e).map(_.name)

  def enumerableNameGenWithDefault[T <: Nameable](e: Enumerable[T], default: T): Gen[Option[String]] =
    enumerableGen(e) map {
      case `default`       => None
      case nonDefaultValue => Some(nonDefaultValue.name)
    }

  def enumerableWithDefaultNameGen[T <: Nameable](e: messages.EnumerableWithDefault[T]): Gen[Option[String]] =
    enumerableGen(e) map { value =>
      if (value == e.default)
        None
      else
        Some(value.name)
    }

  def words: Gen[String] = listOf(oneOf(const(' '), alphaNumChar)).map(_.mkString)

}
