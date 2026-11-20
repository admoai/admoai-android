Feature: An app opts in to Lead Generation and declares which standard values it holds
  As a publisher's tech team
  I want Lead Generation support to be declared only when my app explicitly opts in
  So that an SDK upgrade never serves a form my app cannot show, and no user value leaves the device with the ad request

  The request declares key names only. Values are asked for later, after the viewer taps and
  the form's configuration is fixed. Lead Generation is served on apiVersion "2025-11-01";
  "2025-01-01" accepts and ignores the declaration.

  Scenario: an SDK update alone declares nothing
    Given an app that has not opted in to Lead Generation
    When it requests a decision
    Then the request body is byte-for-byte what earlier SDK versions sent

  Scenario: an opted-in app declares the standard values it holds, by key only
    Given an app that opted in with a provider holding full name, email and phone
    When it requests a decision
    Then the request declares Lead Generation support
    And it lists the keys fullName, email and phone
    And it carries no value of any of them

  Scenario: an opted-in app that holds no standard value still declares support
    Given an app that opted in with a provider holding no key
    When it requests a decision
    Then the request declares Lead Generation support with an empty key list

  Scenario: keys are declared once each, in catalog order
    Given an app that opted in with a provider holding gender, email and first name
    When it requests a decision
    Then the keys are listed as firstName, email, gender

  Scenario: a provider that fails declares no key and the request still goes out
    Given an app that opted in with a provider that fails when asked which keys it holds
    When it requests a decision
    Then the request declares Lead Generation support with an empty key list
    And the log names the failure without any message from the provider

  Scenario: a device without a secure form view does not declare support
    Given an app that opted in on a device whose WebView has no origin-scoped message listener
    When it requests a decision
    Then the request body is byte-for-byte what earlier SDK versions sent

  Scenario: an app that turns the opt-in off stops declaring support
    Given an app that opted in and then turned the opt-in off
    When it requests a decision
    Then the request body is byte-for-byte what earlier SDK versions sent

  Scenario: a declaration a request cannot set by hand
    Given an app that has not opted in
    When it builds a request object that already carries a Lead Generation declaration
    Then the request sent carries no declaration

  Scenario: an opted-in app on an older API version is warned that Lead Generation will not serve
    Given an app that opted in without apiVersion "2025-11-01"
    When it requests a decision
    Then a warning says Lead Generation ads are served on apiVersion "2025-11-01" only

  Scenario: an app compiled against the previous release still builds and copies its requests
    Given an app compiled against the release before Lead Generation that constructs and copies a decision request
    When it runs with this SDK
    Then those calls still link and the request carries no declaration
