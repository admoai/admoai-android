Feature: Each served creative carries its own Lead Generation descriptor
  As a publisher's tech team
  I want every creative to tell me whether it opens a Lead Generation form, and whether this SDK can open it
  So that I never show a creative whose tap leads nowhere, and one bad descriptor never costs me the whole response

  The descriptor lives on each creative (data[].creatives[].interactiveExperience), never on the
  placement or the response. Its version and prefill keys are provisional: the form's version is
  fixed only when the viewer opens it.

  Scenario: a creative without a descriptor decodes as before
    Given a decision response whose creative has no interactiveExperience
    When the SDK decodes it
    Then the creative has no interactive experience

  Scenario: a Lead Generation descriptor is usable
    Given a decision response whose creative carries the contract's Lead Generation descriptor
    When the SDK decodes it
    Then the creative's experience is Lead Generation
    And it exposes the form, the form instance, the prepared version, the expiry and the prefill keys

  Scenario: each creative keeps its own descriptor
    Given a decision response with two placements whose creatives carry different form instances
    When the SDK decodes it
    Then each creative exposes its own form instance

  Scenario: an experience type this SDK does not know is unsupported
    Given a decision response whose creative carries an experience of an unknown type
    When the SDK decodes it
    Then the creative's experience is unsupported and names that type
    And every other part of the response decodes

  Scenario Outline: a malformed descriptor is unsupported and never fails the response
    Given a decision response whose creative carries a descriptor with <defect>
    When the SDK decodes it
    Then the creative's experience is unsupported
    And every other part of the response decodes

    Examples:
      | defect                              |
      | no preparation token                |
      | a blank form instance               |
      | a launch URL that is not HTTPS      |
      | an expiry that is not a timestamp   |
      | prefill keys that are not a list    |
      | a value that is not an object       |

  Scenario: a prefill key this SDK does not know is dropped
    Given a decision response whose descriptor lists fullName, homeAddress and email
    When the SDK decodes it
    Then the prefill keys are fullName and email

  Scenario: the preparation token is never printed
    Given a decoded Lead Generation descriptor
    When the SDK prints the creative or logs the HTTP response with logging on
    Then the preparation token does not appear

  Scenario: an app compiled against the previous release still builds and copies creatives
    Given an app compiled against the release before Lead Generation that constructs and copies a creative
    When it runs with this SDK
    Then those calls still link and a copy keeps the creative's descriptor
