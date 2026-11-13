Feature: Device carrier and category are collected for the publisher without any permission
  As a publisher's tech team
  I want the SDK to fill the device carrier and category when I give it a context
  So that reporting and targeting by carrier and device class work without extra code or permissions

  The category is a property of the device, not of the window: split-screen and rotation never
  change it. The carrier is the operator of the default data subscription. Placeholder names the
  platform reports when there is no operator are never sent.

  Scenario: a phone reports the phone category
    Given a device whose shortest display side is well under 600 dp
    When the app builds its device details with a context
    Then the category is phone

  Scenario: a display 600 dp wide on its shortest side reports tablet
    Given a device whose shortest display side is exactly 600 dp
    When the app builds its device details with a context
    Then the category is tablet

  Scenario: a display 599 dp wide on its shortest side reports phone
    Given a device whose shortest display side is just under 600 dp
    When the app builds its device details with a context
    Then the category is phone

  Scenario: rotation does not change the category
    Given a tablet display
    When the device is held in portrait and in landscape
    Then the category is tablet both times

  Scenario: an unreadable display reports other
    Given a device whose display or density cannot be read
    When the app builds its device details with a context
    Then the category is other

  Scenario: the carrier is the operator of the default data subscription
    Given a device with several subscriptions and a default data subscription
    When the app builds its device details with a context
    Then the carrier is the operator name of the default data subscription
    And no permission is requested

  Scenario: placeholder carrier names are not sent
    Given a device that reports an empty name, a dash placeholder, unknown or wifi as its operator
    When the app builds its device details with a context
    Then no carrier is sent

  Scenario: a carrier name is trimmed and keeps its case
    Given a device whose operator name has surrounding spaces and mixed case
    When the app builds its device details with a context
    Then the carrier is sent trimmed, with its case unchanged

  Scenario: a carrier the app sets by hand is cleaned the same way
    Given an app that sets the carrier by hand to a placeholder or to a padded name
    When it requests a decision
    Then a placeholder sends no carrier and a padded name is sent trimmed

  Scenario: an app that configures the device without a context sends no carrier or category
    Given an app that builds its device details without a context
    When it requests a decision
    Then no carrier and no category are sent

  Scenario: turning off device collection drops carrier and category
    Given an app that has a carrier and a category on its device details
    And device collection is turned off
    When it requests a decision
    Then no device details are sent
