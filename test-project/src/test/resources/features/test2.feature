Feature: Three

  Scenario Outline: Scenario 4
    Given I kill thread if <value> is more than 20

    Examples:
      | value |
      | 75    |
      | 0     |
      | 12    |

  Scenario Outline: Scenario 5
    Given I kill thread if <value> is more than 20

    Examples:
      | value |
      | 65    |
      | 4     |
      | 120   |
      | 44    |
      | 10000 |