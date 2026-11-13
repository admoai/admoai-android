Feature: Device carrier and category are collected for the publisher without any permission
  As a publisher's tech team
  I want the SDK to fill the device carrier and category when I give it a context
  So that reporting and targeting by carrier and device class work without extra code or permissions

  The category is a property of the device, not of the window: split-screen and rotation never
  change it. The carrier is the operator of the default data subscription, and nothing else: with no telephony or no default data subscription, no carrier is sent. Known TV, watch, automotive and desktop hardware is always other; an unreadable display sends no category. Placeholder names the
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

  Scenario: a tv, watch, car or desktop device reports other whatever its screen size
    Given a device that identifies as a tv, a watch, an automotive unit or a desktop
    When the app builds its device details with a context
    Then the category is other, even if its screen is 600 dp or wider

  Scenario: a phone in car mode still reports phone
    Given a handheld phone whose UI is in car mode
    When the app builds its device details with a context
    Then the category is phone

  Scenario: an unreadable display sends no category
    Given a device whose display or density cannot be read
    When the app builds its device details with a context
    Then no category is sent

  Scenario: the carrier is the operator of the default data subscription
    Given a device with several subscriptions and a default data subscription
    When the app builds its device details with a context
    Then the carrier is the operator name of the default data subscription
    And no permission is requested

  Scenario: a device without telephony sends no carrier
    Given a device that has no telephony feature
    When the app builds its device details with a context
    Then no carrier is sent

  Scenario: no default data subscription sends no carrier
    Given a device with telephony but no default data subscription
    When the app builds its device details with a context
    Then no carrier is sent
    And the default voice SIM or any other subscription is never used

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
    And nothing is read from the platform, even if an earlier configuration registered a context

  Scenario: carrier and category are read fresh for every request
    Given an app that configured its device details with a context
    And the device reports Movistar and a phone for the first request
    When the SIM and the display change before the second request
    Then the first request carries Movistar and phone and the second carries Claro and tablet
    And the device details object the app configured is unchanged

  Scenario: a value the app sets wins over the collected one
    Given an app that configured its device details with a context
    And set the carrier, the category or both by hand
    When it requests a decision
    Then the value set by hand is sent and the platform is not asked for it
    And a signal left unset is still collected

  Scenario: a cleared device configuration sends nothing until it is set again
    Given an app that configured its device details with a context
    When it clears the device configuration
    Then a request carries no device details and nothing is read from the platform
    And a cleared configuration the app sets again sends no carrier or category

  Scenario: one failing reader does not affect the other
    Given an app that configured its device details with a context
    And the platform fails while reading the carrier
    When it requests a decision
    Then the category and the other device fields are still sent without a carrier

  Scenario: the SDK keeps only the application context
    Given an app that configures its device details with an activity
    When the SDK registers the context
    Then it holds the application context and never the activity

  Scenario: turning off device collection drops carrier and category
    Given an app that has a carrier and a category on its device details
    And device collection is turned off
    When it requests a decision
    Then no device details are sent

  Scenario: turning off app collection keeps carrier and category
    Given an app that has a carrier and a category on its device details
    And app collection is turned off
    When it requests a decision
    Then the device details still include the carrier and the category
