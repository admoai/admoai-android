Feature: Publishers send Standard audience signals and device details with a decision request
  As a publisher's tech team
  I want typed helpers for the Standard audience signals and for device carrier and category
  So that audience targeting and reporting work without hand-building custom targets

  Audience reporting dimensions require apiVersion "2025-11-01"; targeting matches on older
  versions. The SDK never looks up whether a segment is known or archived: the server decides.

  Scenario: a request that uses no audience helper is unchanged on the wire
    Given an app that calls none of the audience helpers
    When it requests a decision
    Then the request body is byte-for-byte what earlier SDK versions sent

  Scenario: each Standard audience helper sends its key with the catalog value
    Given an app that sets every Standard audience helper
    When it requests a decision
    Then each key is sent as a custom target with its catalog value

  Scenario: trip frequency zero is sent as a string, never a number
    Given an app that sets trip frequency to zero
    When it requests a decision
    Then the trip_frequency_30d target carries the string "0"

  Scenario: setting a helper to null removes only that key
    Given an app that has set several audience helpers
    When it sets one helper to null
    Then only that key is removed and the others are still sent

  Scenario: the last write wins between a typed helper and a raw custom target
    Given an app that writes the same key through a typed helper and through a raw custom target
    When it requests a decision
    Then the key is sent once, with the value written last

  Scenario: publisher segments are always sent as a list, even for one code
    Given an app that sets one publisher segment code
    When it requests a decision
    Then publisher_segments is sent as a list with that single code

  Scenario: repeated publisher segment codes are sent once, in first-seen order
    Given an app that sets publisher segment codes with repeats
    When it requests a decision
    Then each code is sent once, in the order it first appeared

  Scenario: an empty publisher segment list sends no segments at all
    Given an app that sets an empty publisher segment list
    When it requests a decision
    Then no publisher_segments target is sent

  Scenario: an invalid publisher segment code rejects the whole call and changes nothing
    Given an app that sets publisher segments including a code with a space or a symbol
    When the call is made
    Then it fails naming the position of the bad code, never echoing it
    And any segments set earlier are untouched

  Scenario: device carrier and category are sent when the app provides them
    Given an app that provides device carrier and category
    When it requests a decision
    Then the device details include the carrier and the category

  Scenario: turning off device collection drops carrier and category with the rest of the device
    Given an app that provides device carrier and category
    And device collection is turned off
    When it requests a decision
    Then no device details are sent

  Scenario: apps compiled against the previous version keep working without recompiling
    Given an app compiled against SDK 1.8 that builds device details with the old constructors
    When it runs against this SDK version
    Then the old constructors and copy functions still link and behave the same
