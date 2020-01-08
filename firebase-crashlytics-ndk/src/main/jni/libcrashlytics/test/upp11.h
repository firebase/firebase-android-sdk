// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


#pragma once
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <iostream>
#include <iterator>
#include <list>
#include <memory>
#include <sstream>
#include <vector>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdexcept>
#include <array>

namespace upp11 {

class TestException {
public:
	const std::string location;
	const std::string message;
	const std::string detail;
	TestException(const std::string &location, const std::string &message,
		      const std::string &detail = std::string())
		: location(location), message(message), detail(detail)
	{
	}
};

class TestSignalAction {
	int signum;
	struct sigaction oldaction;
public:
	TestSignalAction(int signum, void (*action)(int)) : signum(signum), oldaction() {
		sigaction(signum, nullptr, &oldaction);	// check
		if (oldaction.sa_handler == nullptr && oldaction.sa_sigaction == nullptr) {
			struct sigaction newaction;
			std::memset(&newaction, 0, sizeof(newaction));
			sigemptyset(&newaction.sa_mask);
			newaction.sa_handler = action;
			sigaction(signum, &newaction, nullptr);
		}
	}
	~TestSignalAction() {
		if (oldaction.sa_handler != nullptr || oldaction.sa_sigaction != nullptr) {
			sigaction(signum, &oldaction, nullptr);
		}
	}
};

class TestSignalHandler {
	TestSignalAction actionIll;
	TestSignalAction actionFpe;
	TestSignalAction actionSegv;

	static sigjmp_buf &jumpbuf() {
		static sigjmp_buf buf;
		return buf;
	}

	static void action(int sig) {
		siglongjmp(jumpbuf(), sig);
	}

public:
	TestSignalHandler()
		: actionIll(SIGILL, action), actionFpe(SIGFPE, action), actionSegv(SIGSEGV, action)
	{
		if (sigsetjmp(jumpbuf(), 1) != 0) {
			throw std::runtime_error("Test terminated by signal");
		}
	}
};

class TestCollection {
private:
	typedef std::pair<std::string, std::function<void ()>> test_pair_t;
	std::vector<test_pair_t> tests;
	std::vector<std::string> suites;

	std::string checkpoint_location;
	std::string checkpoint_message;

	TestCollection(): tests(), suites(), checkpoint_location(), checkpoint_message()
	{
	}

	bool invoke(std::function<void ()> test_invoker) const {
		try {
			TestSignalHandler sighandler;
			test_invoker();
		} catch (const TestException &e) {
			std::cout << e.location << ": " << e.message << std::endl;
			if (!e.detail.empty()) {
				std::cout << "\t" << e.detail << std::endl;
			}
			return false;
		} catch (const std::exception &e) {
			std::cout << "unexpected test termination: " << e.what() << std::endl;
			std::cout << checkpoint_location << ": last checkpoint: " << checkpoint_message << std::endl;
			return false;
		} catch (...) {
			std::cout << "unexpected test termination" << std::endl;
			std::cout << checkpoint_location << ": last checkpoint: " << checkpoint_message << std::endl;
			return false;
		}
		return true;
	}

	bool missPatterns(const std::vector<std::string> &patterns, const test_pair_t &test) {
		if (patterns.empty()) { return false; }
		for (const auto &p: patterns) {
			if (test.first.find(p) != std::string::npos) { return false; }
		}
		return true;
	}

public:
	static TestCollection &getInstance() {
		static TestCollection collection;
		return collection;
	}

	void beginSuite(const std::string &name) {
		suites.push_back(name);
	}

	void endSuite() {
		suites.pop_back();
	}

	void addTest(const std::string &name, std::function<void ()> test) {
		std::string path;
		for (auto s: suites) {
			path += s + "::";
		}
		tests.push_back(std::make_pair(path + name, test));
	}

	bool runAllTests(const std::vector<std::string> &patterns, unsigned seed, bool quiet, bool timestamp)
	{
		// Remove all tests not match to pattern
		auto new_end = std::remove_if(tests.begin(), tests.end(),
			std::bind(&TestCollection::missPatterns, this, patterns, std::placeholders::_1));
		tests.erase(new_end, tests.end());
		// Sort by name
		std::sort(tests.begin(), tests.end(),
			[](const test_pair_t &A, const test_pair_t &B){ return A.first < B.first; });
		if (seed != 0) {
			if (!quiet) {
				std::cout << "random seed: " << seed << std::endl;
			}
			std::default_random_engine r(seed);
			std::shuffle(tests.begin(), tests.end(), r);
		}
		int failures = 0;
		for (auto t: tests) {
			using namespace std::chrono;
			const high_resolution_clock::time_point st = high_resolution_clock::now();
			const bool success = invoke(t.second);
			const high_resolution_clock::time_point et = high_resolution_clock::now();
			const unsigned us = duration_cast<microseconds>(et - st).count();
			if (!quiet || !success) {
				std::cout << t.first;
				if (timestamp) {
					std::cout << " (" << us << "us)";
				}
				std::cout << ": " << (success ? "SUCCESS" : "FAIL") << std::endl;
			}
			failures += (success ? 0 : 1);
		}
		std::cout << "Run " << tests.size() << " tests "
			<< "with " << failures << " failures" << std::endl;
		return failures == 0;
	}

	void checkpoint(const std::string &location, const std::string &message) {
		checkpoint_location = location;
		checkpoint_message = message;
	}
};

class TestSuiteBegin {
public:
	TestSuiteBegin(const std::string &name) {
		TestCollection::getInstance().beginSuite(name);
	}
};

class TestSuiteEnd {
public:
	TestSuiteEnd() {
		TestCollection::getInstance().endSuite();
	}
};

template <typename T>
struct TestValue {
	const std::vector<T> value;
	const bool agregate;

	TestValue(const T &iv)
		: value(1, iv), agregate(false) {}
	template<typename I>
	TestValue(const I &begin, const I &end)
		: value(begin, end), agregate(true) {}
};

struct TestValueFactory {
	template <typename T>
	static TestValue<T> create(const T &t) {
		return TestValue<T>(t);
	}
	template<typename T>
	static TestValue<T> create(const std::initializer_list<T> &t) {
		return TestValue<T>(t.begin(), t.end());
	}
	template<typename T>
	static TestValue<T> create(const std::initializer_list<const T> &t) {
		return TestValue<T>(t.begin(), t.end());
	}
	template<typename T>
	static TestValue<T> create(const std::list<T> &t) {
		return TestValue<T>(t.begin(), t.end());
	}
	template<typename T>
	static TestValue<T> create(const std::vector<T> &t) {
		return TestValue<T>(t.begin(), t.end());
	}
	template<typename T, std::size_t size>
	static TestValue<T> create(const std::array<T, size> &t) {
		return TestValue<T>(t.begin(), t.end());
	}
	template<typename T, std::size_t size>
	static TestValue<T> create(const T (&t)[size]) {
		return TestValue<T>(&t[0], &t[size]);
	}
	static TestValue<std::string> create(const char *t) {
		return TestValue<std::string>(t);
	}
};

class TestEqual {
	template <typename A, typename B, typename AI, typename BI>
	bool isEqualDiffer(const TestValue<A> &, const TestValue<B> &, AI, BI) const {
		return false;
	}

	template <typename A, typename B>
	bool isEqualSign(const TestValue<A> &ta, const TestValue<B> &tb) const {
		const B abmin = (std::is_unsigned<A>::value || std::is_unsigned<B>::value) ?
			0 : std::numeric_limits<A>::min() /* both signed */ ;
		const B abmax = (std::is_signed<A>::value || std::is_unsigned<B>::value ||
				 sizeof(A) < sizeof(B))
			? std::numeric_limits<A>::max() : std::numeric_limits<B>::max();
		for (size_t i = 0; i < ta.value.size(); i++) {
			if (tb.value[i] < abmin || tb.value[i] > abmax) return false;
			if (ta.value[i] != tb.value[i]) return false;
		}
		return true;
	}
	template <typename A, typename B>
	bool isEqualDiffer(const TestValue<A> &ta, const TestValue<B> &tb,
			   std::true_type, std::true_type) const
	{
		if (ta.agregate != tb.agregate) return false;
		if (ta.value.size() != tb.value.size()) return false;
		if (sizeof(A) <= sizeof(B)) return isEqualSign(ta, tb);
		return isEqualSign(tb, ta);
	}

	template <typename A, typename B>
	bool isEqualValue(const TestValue<A> &ta, const TestValue<B> &tb) const {
		return isEqualDiffer(ta, tb, typename std::is_integral<A>::type(),
				typename std::is_integral<B>::type());
	}

	// Compare equivalent values
	template <typename T>
	bool isEqualValue(const TestValue<T> &ta, const TestValue<T> &tb) const {
		return ta.agregate == tb.agregate && ta.value == tb.value;
	}
public:
	virtual ~TestEqual();
	template <typename A, typename B>
	bool isEqual(const A &a, const B &b) const {
		const auto ta = TestValueFactory::create(a);
		const auto tb = TestValueFactory::create(b);
		return isEqualValue(ta, tb);
	}
};

TestEqual::~TestEqual() = default;

class TestPrinter {
protected:
	template <typename T>
	std::string printableValue(const TestValue<T> &tt) const {
		std::ostringstream os;
		if (tt.agregate) { os << "{ "; }
		for (size_t p = 0; p < tt.value.size(); p++) {
			os << tt.value[p] << (p + 1 < tt.value.size() ? ", " : "");
		}
		if (tt.agregate) { os << " }"; }
		return os.str();
	}
	std::string printableValue(const TestValue<std::string> &tt) const {
		std::ostringstream os;
		if (tt.agregate) { os << "{ "; }
		for (size_t p = 0; p < tt.value.size(); p++) {
			os << "\"" << tt.value[p] << "\"" << (p + 1 < tt.value.size() ? ", " : "");
		}
		if (tt.agregate) { os << " }"; }
		return os.str();
	}
public:
	virtual ~TestPrinter();
	template <typename T>
	std::string printable(const T &t) const {
		return printableValue(TestValueFactory::create(t));
	}
	template <typename... T> std::string printable(const std::tuple<T...> &t) const;
        template <typename T, typename U> std::string printable(const std::pair<T, U> &t) const;
};

TestPrinter::~TestPrinter() = default;

template <typename T, size_t index = std::tuple_size<T>::value - 1>
struct TestAgregatePrinter : private TestPrinter {
	std::string printable(const T &t) const {
		const std::string value = printableValue(TestValueFactory::create(std::get<index>(t)));
		return TestAgregatePrinter<T, index - 1>().printable(t) + ", " + value;
	}
};
template <typename T>
struct TestAgregatePrinter<T, 0> : private TestPrinter {
	std::string printable(const T &t) const {
		return printableValue(TestValueFactory::create(std::get<0>(t)));
	}
};

template <typename... T>
std::string TestPrinter::printable(const std::tuple<T...> &t) const {
	return TestAgregatePrinter<std::tuple<T...>>().printable(t);
}
template <typename T, typename U>
std::string TestPrinter::printable(const std::pair<T, U> &t) const {
    return TestAgregatePrinter<std::pair<T, U>>().printable(t);
}

class TestAssert : private TestEqual, private TestPrinter {
	const std::string location;

	template <typename A, typename B>
	std::string vsPrint(const A &a, const B &b) const {
		return printable(a) + " vs " + printable(b);
	}

public:
	TestAssert(const std::string &location) : location(location) {}

	template <typename A, typename B>
	void assertEqual(const A &a, const B &b, const std::string &expression) const
	{
		if (isEqual(a, b)) { return; }
		throw TestException(location, "check equal (" + expression + ") failed", vsPrint(a, b));
	}

	template <typename A, typename B>
	void assertNe(const A &a, const B &b, const std::string &expression) const
	{
		if (!isEqual(a, b)) { return; }
		throw TestException(location, "check not equal (" + expression + ") failed", vsPrint(a, b));
	}

	void assertTrue(bool expr, const std::string &expression) const
	{
		if (expr) { return; }
		throw TestException(location, "check " + expression + " failed");
	}
};

template <typename E>
struct TestExceptionChecker {
	const std::string location;
	const std::string extype;

	TestExceptionChecker(const std::string &location, const std::string &extype)
		: location(location), extype(extype)
	{
	}

	void check(const std::function<void ()> &f) {
		try {
			f();
		} catch (const E &e) {
			return;
		} catch (...) {
		}
		throw TestException(location, "expected exception " + extype + " not throw");
	}

	void check(const std::string &message, const std::function<void ()> &f) {
		if (!std::is_convertible<E, std::exception>::value) {
			throw TestException(location, "expected exception " + extype +
					    " is not child of std::exception");
		}
		bool catched = false;
		try {
			try {
				f();
			} catch (const E &) {
				catched = true;
				throw;
			}
		} catch (const std::exception &e) {
			if (catched) {
				if (e.what() == message) { return; }
				throw TestException(location,
					"check exception " + extype + "(\"" + message + "\") failed",
					"catched exception: \"" + std::string(e.what()) + "\"");
			}
		} catch (...) {
		}
		throw TestException(location, "expected exception " + extype + "(\"" + message + "\") not throw");
	}
};

template <typename T>
class TestInvoker {
	const std::string location;
protected:
	virtual ~TestInvoker();
public:
	TestInvoker(const std::string &location) : location(location) { }

	void invoke(std::function<void (T *)> test_function) const {
		TestCollection::getInstance().checkpoint(location, "fixture setUp");
		T instance;

		TestCollection::getInstance().checkpoint(location, "run test");
		test_function(&instance);

		TestCollection::getInstance().checkpoint(location, "fixture tearDown");
	}
};

template <typename T>
TestInvoker<T>::~TestInvoker() = default;

template <typename T>
class TestInvokerTrivial : public TestInvoker<T> {
private:
	void invoke() {
		TestInvoker<T>::invoke(std::bind(&T::run, std::placeholders::_1));
	}
public:
	TestInvokerTrivial(const std::string &location, const std::string &name)
		: TestInvoker<T>(location)
	{
		TestCollection::getInstance().addTest(name,
			std::bind(&TestInvokerTrivial::invoke, this));
	}
};

template <typename T, typename C>
class TestInvokerParametrized : public TestInvoker<T> {
private:
	void invoke(const typename C::value_type &params) {
		TestInvoker<T>::invoke(std::bind(&T::run, std::placeholders::_1, params));
	}
public:
	TestInvokerParametrized(const std::string &location, const std::string &name, const C &params)
		: TestInvoker<T>(location)
	{
		for (const auto v: params) {
			TestCollection::getInstance().addTest(
				name + "<" + TestPrinter().printable(v) + ">",
				std::bind(&TestInvokerParametrized::invoke, this, v));
		}
	}
};

class TestMain {
public:
	int main(int argc, char **argv) {
		bool quiet = false;
		bool timestamp = false;
		int seed = time(0);
		std::vector<std::string> patterns;
		while (true) {
			int opt = getopt(argc, argv, "qts:r:");
			if (opt == -1) { break; }
			if (opt == 'q') { quiet = true; }
			if (opt == 't') { timestamp = true; }
			if (opt == 's') { seed = std::atoi(optarg); }
			if (opt == 'r') { patterns.push_back(optarg); }
		};
		return TestCollection::getInstance().runAllTests(patterns, seed, quiet, timestamp) ? 0 : -1;
	}
};

} // end of namespace upp11

// Workaround for preprocessor number to string conversion
#define LINE_TEXT_(x) #x
#define LINE_TEXT(x) LINE_TEXT_(x)
#define LOCATION __FILE__ "(" LINE_TEXT(__LINE__) ")"

#define UP_MAIN() \
int main(int argc, char **argv) { \
	return upp11::TestMain().main(argc, argv); \
}

#define UP_RUN() \
upp11::TestCollection::getInstance().runAllTests({}, 0, false, false)

#define UP_SUITE_BEGIN(name) \
namespace name { \
	static upp11::TestSuiteBegin suite_begin(#name);

#define UP_SUITE_END() \
	static upp11::TestSuiteEnd suite_end; \
}

#define UP_TEST(testname) \
struct testname { \
	void run(); \
}; \
static upp11::TestInvokerTrivial<testname> testname##_invoker(LOCATION, #testname); \
void testname::run()

#define UP_FIXTURE_TEST(testname, fixture) \
struct testname : public fixture { \
	void run(); \
}; \
static upp11::TestInvokerTrivial<testname> testname##_invoker(LOCATION, #testname); \
void testname::run()

#define UP_PARAMETRIZED_TEST(testname, params) \
struct testname { \
	void run(const decltype(params)::value_type &params); \
}; \
static upp11::TestInvokerParametrized<testname, decltype(params)> \
	testname##_invoker(LOCATION, #testname, params); \
void testname::run(const decltype(params)::value_type &params)

#define UP_FIXTURE_PARAMETRIZED_TEST(testname, fixture, params) \
struct testname : public fixture { \
	void run(const decltype(params)::value_type &params); \
}; \
static upp11::TestInvokerParametrized<testname, decltype(params)> \
	testname##_invoker(LOCATION, #testname, params); \
void testname::run(const decltype(params)::value_type &params)

#define UP_ASSERT(...) \
upp11::TestCollection::getInstance().checkpoint(LOCATION, "UP_ASSERT"), \
upp11::TestAssert(LOCATION).assertTrue(__VA_ARGS__, #__VA_ARGS__)

#define UP_ASSERT_EQUAL(...) \
upp11::TestCollection::getInstance().checkpoint(LOCATION, "UP_ASSERT_EQUAL"), \
upp11::TestAssert(LOCATION).assertEqual(__VA_ARGS__, #__VA_ARGS__)

#define UP_ASSERT_NE(...) \
upp11::TestCollection::getInstance().checkpoint(LOCATION, "UP_ASSERT_NE"), \
upp11::TestAssert(LOCATION).assertNe(__VA_ARGS__, #__VA_ARGS__)

#define UP_ASSERT_EXCEPTION(extype, ...) \
upp11::TestCollection::getInstance().checkpoint(LOCATION, "UP_ASSERT_EXCEPTION"), \
upp11::TestExceptionChecker<extype>(LOCATION, #extype).check(__VA_ARGS__)

#define UP_CHECKPOINT(...) \
upp11::TestCollection::getInstance().checkpoint(LOCATION, __VA_ARGS__)
