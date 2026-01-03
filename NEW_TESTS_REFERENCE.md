# New Test Methods Reference

## Quick Index of All Added Tests

### FileLoaderTest (5 new tests)
```java
// Tests public constructor with default ClassLoader
publicConstructorLoadsFileFromClasspath()

// Tests error handling for missing files
publicConstructorWithNonExistentFileThrows()

// Tests that multiple reader calls return independent instances
toReaderMultipleCallsReturnIndependentReaders()

// Tests null resource handling from ClassLoader
fileLoaderWithNullResourceThrows()

// Tests content preservation across multiple calls
fileLoaderPreservesFileContent()
```

### KeyPasswordTest (8 new tests)
```java
// Tests UTF-8 encoding with multi-byte characters (ñ)
toUtf8BytesWithSpecialCharactersEncodesCorrectly()

// Tests boundary condition with exactly 32 ASCII characters
toUtf8BytesWithExactly32BytesSucceeds()

// Tests encoding with emoji and mixed Unicode characters
toUtf8BytesWithUnicodeCharactersEncodesCorrectly()

// Tests that empty char array is rejected
ofWithEmptyCharArrayThrowsIllegalArgumentException()

// Tests that empty string is rejected
fromStringWithEmptyStringThrowsIllegalArgumentException()

// Tests KeyPassword inherits from Password correctly
keyPasswordInheritsPasswordBehavior()

// Tests that inherited clear() method works
keyPasswordClearMethodWorks()

// Tests that each call returns a new array instance
toUtf8BytesReturnsNewArrayEachCall()
```

### DatabaseContextFactoryTest (6 new tests)
```java
// Tests successful connection validation
testValidateConnectionWithValidConnectionSucceeds()

// Tests handling of invalid connection
testValidateConnectionWithInvalidConnectionThrows()

// Tests SQLException handling in validateConnection
testValidateConnectionWithSQLExceptionThrows()

// Tests DataSource creation from config
testCreateDataSourceReturnsValidDataSource()

// Tests that timeout constants are properly defined
testConnectionTimeoutConstantsAreSet()

// Integration test for factory methods with mocked DataSource
testFactoryMethodsWithValidConfigAndDataSource()
```

---

## Test Execution Commands

### Run All New Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
# FileLoader tests
./gradlew test --tests FileLoaderTest

# KeyPassword tests
./gradlew test --tests KeyPasswordTest

# DatabaseContextFactory tests
./gradlew test --tests DatabaseContextFactoryTest
```

### Run Specific Test Method
```bash
# Example: Run a single test
./gradlew test --tests FileLoaderTest.publicConstructorLoadsFileFromClasspath
```

### Generate Coverage Report
```bash
./gradlew clean test jacocoTestReport
```

---

## Test Coverage by Category

### Error Handling Tests
- `publicConstructorWithNonExistentFileThrows()`
- `fileLoaderWithNullResourceThrows()`
- `ofWithEmptyCharArrayThrowsIllegalArgumentException()`
- `fromStringWithEmptyStringThrowsIllegalArgumentException()`
- `testValidateConnectionWithInvalidConnectionThrows()`
- `testValidateConnectionWithSQLExceptionThrows()`

### Edge Case Tests
- `toUtf8BytesWithSpecialCharactersEncodesCorrectly()`
- `toUtf8BytesWithExactly32BytesSucceeds()`
- `toUtf8BytesWithUnicodeCharactersEncodesCorrectly()`
- `toReaderMultipleCallsReturnIndependentReaders()`

### Security/Behavior Tests
- `keyPasswordClearMethodWorks()`
- `toUtf8BytesReturnsNewArrayEachCall()`
- `fileLoaderPreservesFileContent()`
- `keyPasswordInheritsPasswordBehavior()`

### Integration Tests
- `publicConstructorLoadsFileFromClasspath()`
- `testValidateConnectionWithValidConnectionSucceeds()`
- `testCreateDataSourceReturnsValidDataSource()`
- `testFactoryMethodsWithValidConfigAndDataSource()`
- `testConnectionTimeoutConstantsAreSet()`

---

## Coverage Metrics by Test File

### FileLoaderTest
- **Before:** 87.5% method, 83.3% branch, 89.5% line
- **After:** ~100% method, ~90% branch, ~95% line
- **Tests Added:** 5
- **Total Tests:** 9

### KeyPasswordTest
- **Before:** 100% method, 85.7% branch, 100% line
- **After:** 100% method, ~95% branch, 100% line
- **Tests Added:** 8
- **Total Tests:** 21

### DatabaseContextFactoryTest
- **Before:** 80% method, 84.2% branch, 52.2% line
- **After:** ~95% method, ~95% branch, ~85% line
- **Tests Added:** 6
- **Total Tests:** 15

---

## Implementation Details

### Test Patterns Used

#### 1. Exception Testing
```java
assertThrows(ExceptionType.class, () -> methodCall());
```

#### 2. Mock Object Testing
```java
DataSource mockDataSource = mock(DataSource.class);
when(mockDataSource.getConnection()).thenReturn(mockConnection);
```

#### 3. Boundary Testing
```java
assertEquals(32, bytes.length); // Exact boundary
assertTrue(bytes.length > 32);  // Greater than boundary
```

#### 4. Behavior Verification
```java
verify(mockConnection).isValid(timeout);
verify(mockConnection).close();
```

#### 5. Array/Content Testing
```java
assertArrayEquals(expected, actual);
assertNotSame(array1, array2);
```

---

## Notes

- All tests follow existing project conventions
- Tests use Mockito for mocking
- Tests are independent and can run in any order
- No external dependencies added
- All tests are deterministic and repeatable
- Tests compile without warnings
- All tests pass with 100% success rate

---

## Related Documentation

- `CODE_COVERAGE_ANALYSIS.md` - Detailed coverage analysis
- `TEST_IMPROVEMENTS_SUMMARY.md` - Implementation summary
- `COVERAGE_IMPROVEMENT_REPORT.md` - Final report with metrics
