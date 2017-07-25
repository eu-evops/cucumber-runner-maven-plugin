Feature: Two

  Scenario Outline: Scenario 2
    Given I kill thread if <value> is more than 20

    Examples:
      | value |
      | 5    |

  Scenario Outline: Scenario 3
    Given I kill thread if <value> is more than 20

    Examples:
      | value |
      | 10    |
      | 15    |