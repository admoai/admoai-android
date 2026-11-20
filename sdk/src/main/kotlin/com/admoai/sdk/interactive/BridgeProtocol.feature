Feature: The SDK and the hosted form trust only well-formed bridge messages of their own form
  As a publisher's tech team
  I want the SDK to accept only the messages the shared contract allows, from this form, in order
  So that a page cannot drive my app with a message that only looks familiar

  The rules come from the shared contract pack's bridge-messages.json, read as data: size,
  envelope, instance, channel, type, payload bounds and the order in which messages may arrive.

  Scenario: every valid runtime message of the contract is accepted in its order
    Given a bridge for the contract's instance and channel
    When each valid runtime fixture arrives after the messages it depends on
    Then every one is accepted

  Scenario: every invalid runtime message of the contract is refused
    Given a bridge for the contract's instance and channel
    When each invalid runtime fixture arrives
    Then every one is refused

  Scenario: a message for another instance or channel is refused
    Given a bridge for the contract's instance and channel
    When a valid message arrives for another instance, or on another channel
    Then it is refused

  Scenario: a message that arrives before the message it depends on is refused
    Given a bridge that has not exchanged initialize
    When prepared arrives
    Then it is refused

  Scenario: a message over the size limit is refused before it is parsed
    Given a bridge for the contract's instance and channel
    When a message larger than the contract's limit arrives
    Then it is refused

  Scenario: a page that speaks an unknown bridge version is refused
    Given a bridge for the contract's instance and channel
    When bridge_ready arrives with a bridge version this SDK does not know
    Then it is refused

  Scenario: messages the SDK sends match the contract byte for byte in meaning
    Given a bridge for the contract's instance and channel
    When the SDK encodes each app message of the contract
    Then each equals its contract fixture as JSON

  Scenario: text with quotes, newlines and Unicode crosses the bridge as data
    Given a bridge for the contract's instance and channel
    When the SDK encodes a value with quotes, a newline, an emoji and a script tag
    Then the receiver decodes exactly that value
    And no part of it is executable

  Scenario: a refused message is never echoed
    Given a bridge for the contract's instance and channel
    When a malformed message carrying a personal value is refused
    Then the refusal names its reason without any part of the message
