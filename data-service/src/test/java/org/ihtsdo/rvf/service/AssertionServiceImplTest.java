package org.ihtsdo.rvf.service;

import org.ihtsdo.rvf.entity.Assertion;
import org.ihtsdo.rvf.entity.AssertionTest;
import org.ihtsdo.rvf.entity.ReleaseCenter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
@Transactional
public class AssertionServiceImplTest {

	@Autowired
	private AssertionService assertionService;
    @Autowired
    private EntityService entityService;
    private Assertion assertion;
    private ReleaseCenter releaseCenter;
    private AssertionTest assertionTest;
    private org.ihtsdo.rvf.entity.Test test;

    @Test
	@Rollback
	public void testCreate() throws Exception {
		assertNotNull("id should be set", assertion.getId());
	}

	@Test
	@Rollback
	public void testFindAll() throws Exception {
		try {
			assert assertionService.findAll().contains(assertion);
		} catch (Exception e) {
			fail("No exception expected but got " + e.getMessage());
		}

	}

    @Before
    public void setUp() {
        assert entityService != null;
        assert assertionService != null;
        // ensure database is clean
        assert entityService.count(org.ihtsdo.rvf.entity.Test.class) == 0;
        assert entityService.count(AssertionTest.class) == 0;
        assert entityService.count(ReleaseCenter.class) == 0;
        assert entityService.count(Assertion.class) == 0;

        assertion = new Assertion();
        assertion.setName("Test assertion");
        assertion.setDescription("Test Description");
        assertion = assertionService.create(assertion);
        assertNotNull(assertion.getId());
        assertNotNull(assertion.getUuid());

        // create test
        test = new org.ihtsdo.rvf.entity.Test();
        test.setName("Test 1");
        test = (org.ihtsdo.rvf.entity.Test) entityService.create(test);
        assert test != null;
        assert test.getId() != null;
        assert entityService.count(org.ihtsdo.rvf.entity.Test.class) > 0;

        // create release centre
        releaseCenter = new ReleaseCenter();
        releaseCenter.setName("Test release centre 1");
        releaseCenter = (ReleaseCenter) entityService.create(releaseCenter);
        assert releaseCenter != null;
        assert releaseCenter.getId() != null;
        assert entityService.count(releaseCenter.getClass()) > 0;

        //create assertion test
        assertionTest = new AssertionTest();
        assertionTest.setAssertion(assertion);
        assertionTest.setTest(test);
//        assertionTest.setCenter(releaseCenter);
        assertionTest = (AssertionTest) entityService.create(assertionTest);
        assert assertionTest != null;
        assert assertionTest.getId() != null;
        assert entityService.count(AssertionTest.class) > 0;
    }

    @Test
    public void testFindEntitiesById() throws Exception {
        assertNotNull("Test object must not be null", entityService.find(test.getClass(), test.getId()));
        assertNotNull("AssertionTest object must not be null", entityService.find(assertionTest.getClass(), assertionTest.getId()));
        assertNotNull("ReleaseCentre object must not be null", entityService.find(releaseCenter.getClass(), releaseCenter.getId()));
        assertNotNull("Assertion object must not be null", assertionService.find(assertion.getId()));
        assertNotNull("Assertion object must not be null", assertionService.find(assertion.getUuid()));
    }

    @Test
    @Rollback
    public void testFindAssertionTests() throws Exception {

        // use service to find assertion tests associated with assertion
        List<AssertionTest> assertionTests = assertionService.getAssertionTests(assertion);
        assert  assertionTests != null;
        assert assertionTests.size() > 0;
        assert assertionTests.contains(assertionTest);
    }

    @Test
    @Rollback
    public void testFindTests() throws Exception {

        // use service to find tests associated with assertion
        List<org.ihtsdo.rvf.entity.Test> tests = assertionService.getTests(assertion);
        assert  tests != null;
        assert tests.size() > 0;
        assert tests.contains(test);
    }

    @Test
    @Rollback
    public void testFindAssertionTestsByIds() throws Exception {

        // use service to find assertion tests associated with assertion
        List<AssertionTest> assertionTests = assertionService.getAssertionTests(assertion.getId());
        assert  assertionTests != null;
        assert assertionTests.size() > 0;
        assert assertionTests.contains(assertionTest);
    }

    @Test
    @Rollback
    public void testFindTestsByIds() throws Exception {

        // use service to find tests associated with assertion
        List<org.ihtsdo.rvf.entity.Test> tests = assertionService.getTests(assertion.getId());
        assert  tests != null;
        assert tests.size() > 0;
        assert tests.contains(test);
    }

    @Test
    @Rollback
    public void testFindAssertionTestsUsingUuids() throws Exception {

        // use service to find assertion tests associated with assertion
        List<AssertionTest> assertionTests = assertionService.getAssertionTests(assertion.getUuid());
        assert  assertionTests != null;
        assert assertionTests.size() > 0;
        assert assertionTests.contains(assertionTest);
    }

    @Test
    @Rollback
    public void testFindTestsByAssertion() throws Exception {

        // use service to find tests associated with assertion
        List<org.ihtsdo.rvf.entity.Test> tests = assertionService.getTests(assertion);
        assert  tests != null;
        assert tests.size() > 0;
        assert tests.contains(test);
    }

    @Test
    @Rollback
    public void testFindAssertionTestsByUuids() throws Exception {

        // use service to find assertion tests associated with assertion
        List<AssertionTest> assertionTests = assertionService.getAssertionTests(assertion.getUuid());
        assert  assertionTests != null;
        assert assertionTests.size() > 0;
        assert assertionTests.contains(assertionTest);
    }

    @Test
    @Rollback
    public void testFindTestsByUuids() throws Exception {

        // use service to find tests associated with assertion
        List<org.ihtsdo.rvf.entity.Test> tests = assertionService.getTests(assertion.getUuid());
        assert  tests != null;
        assert tests.size() > 0;
        assert tests.contains(test);
    }

    @Test
    @Rollback
    public void testAddTestToAssertion() throws Exception {
        int originalCount = assertionService.getAssertionTests(assertion).size();
        Assertion returnedAssertion = assertionService.addTest(assertion, getRandomTest());
        assert returnedAssertion != null;
        assert assertionService.getAssertionTests(assertion).size() > originalCount;
    }

    @Test
    @Rollback
    public void testAddTestToAssertionWithReleaseCentre() throws Exception {
        int originalCount = assertionService.getAssertionTests(assertion).size();
        Assertion returnedAssertion = assertionService.addTest(assertion, getRandomTest());
        assert returnedAssertion != null;
        assert assertionService.getAssertionTests(assertion).size() > originalCount;
    }

    @Test
    @Rollback
    public void testAddTestCollectionToAssertion() throws Exception {
        int originalCount = assertionService.getAssertionTests(assertion).size();
        List<org.ihtsdo.rvf.entity.Test> tests = new ArrayList<>();
        tests.add(getRandomTest());
        tests.add(getRandomTest());
        Assertion returnedAssertion = assertionService.addTests(assertion, tests);
        assert returnedAssertion != null;
        assert assertionService.getAssertionTests(assertion).size() > originalCount;
    }

    @Test
    @Rollback
    public void testAddTestCollectionToAssertionWithReleaseCentre() throws Exception {
        int originalCount = assertionService.getAssertionTests(assertion).size();
        List<org.ihtsdo.rvf.entity.Test> tests = new ArrayList<>();
        tests.add(getRandomTest());
        tests.add(getRandomTest());
        Assertion returnedAssertion = assertionService.addTests(assertion, tests);
        assert returnedAssertion != null;
        assert assertionService.getAssertionTests(assertion).size() > originalCount;
    }

    private org.ihtsdo.rvf.entity.Test getRandomTest(){
        org.ihtsdo.rvf.entity.Test test = new org.ihtsdo.rvf.entity.Test();
        test.setName("Random Test " + UUID.randomUUID());
        return test;
    }

    @Test
    @Rollback
    public void testDeleteTestToAssertion() throws Exception {
        org.ihtsdo.rvf.entity.Test test = getRandomTest();
        Assertion returnedAssertion = assertionService.addTest(assertion, test);
        int originalCount = assertionService.getAssertionTests(assertion).size();
        assert returnedAssertion != null;

        assertionService.deleteTest(assertion, test);
        assert assertionService.getAssertionTests(assertion).size() < originalCount;
    }

    @Test
    @Rollback
    public void testDeleteTestToAssertionWithReleaseCentre() throws Exception {
        org.ihtsdo.rvf.entity.Test test = getRandomTest();
        Assertion returnedAssertion = assertionService.addTest(assertion, test);
        int originalCount = assertionService.getAssertionTests(assertion).size();
        assert returnedAssertion != null;

        assertionService.deleteTest(assertion, test);
        assert assertionService.getAssertionTests(assertion).size() < originalCount;
    }

    @Test
    @Rollback
    public void testDeleteTestCollectionToAssertion() throws Exception {
        List<org.ihtsdo.rvf.entity.Test> tests = new ArrayList<>();
        tests.add(getRandomTest());
        tests.add(getRandomTest());
        Assertion returnedAssertion = assertionService.addTests(assertion, tests);
        int originalCount = assertionService.getAssertionTests(assertion).size();
        assert returnedAssertion != null;

        assertionService.deleteTests(assertion, tests);
        assert assertionService.getAssertionTests(assertion).size() < originalCount;
    }

    @Test
    @Rollback
    public void testDeleteTestCollectionToAssertionWithReleaseCentre() throws Exception {
        List<org.ihtsdo.rvf.entity.Test> tests = new ArrayList<>();
        tests.add(getRandomTest());
        tests.add(getRandomTest());
        Assertion returnedAssertion = assertionService.addTests(assertion, tests);
        int originalCount = assertionService.getAssertionTests(assertion).size();
        assert returnedAssertion != null;

        assertionService.deleteTests(assertion, tests);
        assert assertionService.getAssertionTests(assertion).size() < originalCount;
    }

//    @Test
//    @Rollback
//    public void testSaveForAssertionGroup() throws Exception {
//        AssertionGroup group = new AssertionGroup();
//        group.setName("Test assertion group");
//        assertion.setGroups(Collections.singleton(group));
//
//        Assertion assertion2 = new Assertion();
//        assertion2.setName("Second assertion in group");
//        assertion2.setDescription("Description of second assertion in group");
//        assertion2.setGroups(Collections.singleton(group));
//
//        // save assertion2
//        assertion2 = assertionService.addTest(assertion2, getRandomTest());
//        assertion = assertionService.update(assertion);
//
//        assertTrue(assertion.getGroups().size() > 0);
//        assertTrue(assertion2.getGroups().size() > 0);
//
//        Assertion retrievedAssertion = assertionService.find(assertion2.getId());
//        assertTrue(retrievedAssertion.getGroups().contains(group));
//    }

    @After
    public void tearDown() throws Exception {
        assert assertionService != null;
        assertionService.delete(assertion);
        assert entityService != null;
        entityService.delete(test);
        entityService.delete(releaseCenter);
        entityService.delete(assertionTest);
        entityService.delete(entityService.getIhtsdo());
    }
}
