package com.vinctus.oql

import typings.node.global.console
import typings.node.processMod.global.process

import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import typings.pg.mod.types.setTypeParser

import scala.async.Async.{async, await}

object Main extends App {

  private val fs = g.require("fs")

  private def readFile(name: String) = {
    fs.readFileSync(name).toString
  }

//  val conn = new RDBConnection(readFile("test/basic.tab"))
//  val oql = new OQL(conn, readFile("test/basic.erd"))
//
//  oql.trace = true
//
//  for {
//    q <- oql.x.insert(Map("a" -> "qwer"))
//  } {
//    println(q)
//  }

//  val conn = new RDBConnection(readFile("test/m2o.tab"))
//  val oql = new OQL(conn, readFile("test/m2o.erd"))

//  val conn = new RDBConnection(readFile("test/m2m.tab"))
//  val oql = new OQL(conn, readFile("test/m2m.erd"))

  val conn = new PostgresConnection("localhost", 5433, "shuttlecontrol", "shuttlecontrol", "shuttlecontrol", false, 10)
  val oql = new OQL(
    conn,
    """
      |  entity account (accounts) {
      |   *id: uuid
      |    name: text!
      |    industry: text!
      |    phoneNumber (phone_number): text
      |    country: text!
      |    uom: text!
      |    enabled: bool!
      |    plan: text!
      |    paymentMethodExempt (payment_method_exempt): bool!
      |    stripeCustomerId (stripe_customer_id): text
      |    stripeSubscriptionId (stripe_subscription_id): text
      |    stripeSubscriptionTripId (stripe_subscription_trip_id): text
      |    stripeSubscriptionSmsId (stripe_subscription_sms_id): text
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    trialEndAt (trial_end_at): timestamp!
      |    stores: [store]
      |    users: [user]
      |  }
      |
      |  entity place (places) {
      |   *id: uuid
      |    store (store_id): store!
      |    latitude: float8!
      |    longitude: float8!
      |    address: text!
      |    isFavorite (is_favorite): bool!
      |    createdAt (created_at): timestamp!
      |  }
      |
      |  entity virtualPhoneNumber (virtual_phone_numbers) {
      |   *id: uuid
      |    digits: text!
      |  }
      |
      |  entity store (stores) {
      |   *id: uuid
      |    enabled: bool!
      |    account (account_id): account!
      |    name: text!
      |    timezoneId (timezone_id): text!
      |    place (place_id): place
      |    color: text!
      |    iconUrl (icon_url): text
      |    markerUrl (marker_url): text
      |    radiusBound (radius_bound): int4
      |    virtualPhoneNumber (virtual_phone_number_id): virtualPhoneNumber!
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |    users: [user] (users_stores)
      |    vehicles: [vehicle]
      |    workflows: [workflow]
      |    trips: [trip]
      |  }
      |
      |  entity user (users) {
      |   *id: uuid
      |    account (account_id): account!
      |    role: text!
      |    email: text!
      |    emailVerified (email_verified): bool!
      |    password: text!
      |    firstName (first_name): text!
      |    lastName (last_name): text!
      |    language: text!
      |    phoneNumber (phone_number): text!
      |    fcmToken (fcm_token): text
      |    enabled: bool!
      |    loginToken (login_token): text!
      |    lastLoginAt (last_login_at): timestamp
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |    stores: [store] (users_stores)
      |    vehicle: <vehicle.driver>
      |  }
      |
      |  entity pendingInvitation (pending_invitations) {
      |   *id: uuid
      |    account (account_id): account!
      |    role: text!
      |    email: text!
      |    stores: [store] (pending_invitations_stores)
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    expiresAt (expires_at): timestamp!
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |  }
      |
      |  entity users_stores {
      |    user (user_id): user
      |    store (store_id): store
      |  }
      |
      |  entity pending_invitations_stores {
      |    pendingInvitation (pending_invitation_id): pendingInvitation
      |    store (store_id): store
      |  }
      |
      |  entity vehicle (vehicles) {
      |   *id: uuid
      |    driver (driver_id): user
      |    type: text!
      |    enabled: bool!
      |    seats: int4!
      |    make: text!
      |    model: text!
      |    color: text!
      |    licensePlate (license_plate): text!
      |    store (store_id): store!
      |    vehicleCoordinate (vehicle_coordinate_id): vehicleCoordinate
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |    trips: [trip]
      |  }
      |
      |  entity vehicleCoordinate (vehicle_coordinates) {
      |   *id: uuid
      |    vehicle (vehicle_id): vehicle!
      |    driver (driver_id): user
      |    latitude: float8!
      |    longitude: float8!
      |    altitude: float8!
      |    accuracy: float8!
      |    altitudeAccuracy (altitude_accuracy): float8!
      |    heading: float8!
      |    speed: float8!
      |    createdAt (created_at): timestamp!
      |  }
      |
      |  entity customer (customers) {
      |   *id: uuid
      |    store (store_id): store!
      |    firstName (first_name): text
      |    lastName (last_name): text
      |    phoneNumber (phone_number): text!
      |    email: text
      |    language: text!
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |    places: [place] (customers_places)
      |    enabled: bool!
      |  }
      |
      |  entity customers_places {
      |    customer (customer_id): customer
      |    place (place_id): place
      |  }
      |
      |  entity messageTemplate (message_templates) {
      |   *id: uuid
      |    name: text!
      |    enabled: bool!
      |    type: text!
      |    store (store_id): store!
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |    locales: [messageTemplateLocale]
      |  }
      |
      |  entity messageTemplateLocale (message_template_locales) {
      |    *id: uuid
      |    messageTemplate (message_template_id): messageTemplate!
      |    language: text!
      |    template: text!
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |  }
      |
      |  entity workflow (workflows) {
      |   *id: uuid
      |    store (store_id): store!
      |    enabled: bool!
      |    name: text!
      |    description: text
      |    steps: [workflowStep]
      |    customerRequired (customer_required): bool!
      |    emailRequired (email_required): bool!
      |    defaultReturnTripWorkflow (default_return_trip_workflow): workflow
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |  }
      |
      |  entity workflowStep (workflow_steps) {
      |   *id: uuid
      |    workflow (workflow_id): workflow!
      |    type: text!
      |    name: text!
      |    place (place_id): place
      |    position: int4!
      |    messageTemplate (message_template_id): messageTemplate
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |  }
      |
      |  entity trip (trips) {
      |   *id: uuid
      |    reference: text!
      |    state: text!
      |    position: int4!
      |    seats: int4!
      |    shortUrl (short_url): text!
      |    customer (customer_id): customer!
      |    store (store_id): store!
      |    workflow (workflow_id): workflow!
      |    vehicle (vehicle_id): vehicle
      |    returnTrip (return_trip_id): trip
      |    returnTripFor (return_trip_for_id): trip
      |    createdAt (created_at): timestamp!
      |    requestedAt (requested_at): timestamp
      |    scheduledAt (scheduled_at): timestamp
      |    confirmedAt (confirmed_at): timestamp
      |    confirmedBy (confirmed_by): user
      |    notifyAt (notify_at): timestamp
      |    updatedAt (updated_at): timestamp
      |    finishedAt (finished_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |    notes: [tripNote]
      |    drivers: [user] (trips_drivers)
      |    steps: [tripStep]
      |  }
      |
      |  entity tripNote (trip_notes) {
      |   *id: uuid
      |    content: text!
      |    trip (trip_id): trip!
      |    createdAt (created_at): timestamp!
      |    updatedAt (updated_at): timestamp
      |    createdBy (created_by): user
      |    updatedBy (updated_by): user
      |  }
      |
      |  entity trips_drivers {
      |    trip (trip_id): trip!
      |    driver (driver_id): user!
      |  }
      |
      |  entity tripStep (trip_steps) {
      |   *id: uuid
      |    type: text!
      |    trip (trip_id): trip!
      |    driver (driver_id): user
      |    name: text!
      |    place (place_id): place
      |    imageUrl (image_url): text
      |    position: int4!
      |    vehicle (vehicle_id): vehicle
      |    distance: float8
      |    finishedAt (finished_at): timestamp
      |    messageTemplate (message_template_id): messageTemplate
      | }""".stripMargin
  )

//  oql.trace = true

  for {
    q <- oql.queryMany("account [name IN :name]", Map("name" -> js.Array("demo")))
  } {
    println(q)
  }

}
