/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.msa.ui.base;

import io.qameta.allure.Allure;
import lombok.SneakyThrows;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.fail;

abstract public class AbstractBasePage {
    public static final long WAIT_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    protected WebDriver driver;
    protected WebDriverWait wait;
    protected Actions actions;
    protected JavascriptExecutor js;

    public AbstractBasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofMillis(WAIT_TIMEOUT));
        this.actions = new Actions(driver);
        this.js = (JavascriptExecutor) driver;
    }

    @SneakyThrows
    protected static void sleep(double second) {
        Thread.sleep((long) (second * 1000L));
    }

    protected WebElement waitUntilVisibilityOfElementLocated(String locator) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(locator)));
        } catch (WebDriverException e) {
            return fail("No visibility element: " + locator);
        }
    }

    protected WebElement waitUntilPresenceOfElementLocated(String locator) {
        try {
            return wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(locator)));
        } catch (WebDriverException e) {
            return fail("No presence element: " + locator);
        }
    }

    protected List<WebElement> waitUntilPresenceOfElementsLocated(String locator) {
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(locator)));
            return driver.findElements(By.xpath(locator));
        } catch (WebDriverException e) {
            return fail("No presence elements: " + locator);
        }
    }

    protected WebElement waitUntilElementToBeClickable(String locator) {
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(By.xpath(locator)));
        } catch (WebDriverException e) {
            return fail("No clickable element: " + locator);
        }
    }

    protected List<WebElement> waitUntilVisibilityOfElementsLocated(String locator) {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(locator)));
            return driver.findElements(By.xpath(locator));
        } catch (WebDriverException e) {
            return fail("No visibility elements: " + locator);
        }
    }

    protected List<WebElement> waitUntilElementsToBeClickable(String locator) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath(locator)));
            return driver.findElements(By.xpath(locator));
        } catch (WebDriverException e) {
            return fail("No clickable elements: " + locator);
        }
    }

    public void waitUntilUrlContainsText(String urlPath) {
        try {
            wait.until(ExpectedConditions.urlContains(urlPath));
        } catch (WebDriverException e) {
            fail("This URL path is missing");
        }
    }

    protected void moveCursor(WebElement element) {
        actions.moveToElement(element).build().perform();
    }

    protected void doubleClick(WebElement element) {
        actions.doubleClick(element).build().perform();
    }

    public boolean elementIsNotPresent(String locator) {
        try {
            return wait.until(ExpectedConditions.not(ExpectedConditions.visibilityOfElementLocated(By.xpath(locator))));
        } catch (WebDriverException e) {
            return fail("Element is present: " + locator);
        }
    }

    public boolean elementIsNotPresent(WebElement element) {
        try {
            return wait.until(ExpectedConditions.not(ExpectedConditions.visibilityOf(element)));
        } catch (WebDriverException e) {
            throw new AssertionError("Element is present");
        }
    }

    public void waitUntilElementNotVisibility(WebElement element) {
        try {
            wait.until(ExpectedConditions.not(ExpectedConditions.visibilityOf(element)));
        } catch (WebDriverException e) {
            fail(element.getTagName() + "is visibility");
        }
    }

    public boolean elementsIsNotPresent(String locator) {
        try {
            return wait.until(ExpectedConditions.not(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath(locator))));
        } catch (WebDriverException e) {
            return fail("Elements is present: " + locator);
        }
    }

    public void waitUntilNumberOfTabToBe(int tabNumber) {
        try {
            wait.until(ExpectedConditions.numberOfWindowsToBe(tabNumber));
        } catch (WebDriverException e) {
            fail("No tabs with this number: " + tabNumber);
        }
    }

    public void jsClick(WebElement element) {
        js.executeScript("arguments[0].click();", element);
    }

    public void enterText(WebElement element, CharSequence keysToEnter) {
        element.click();
        element.sendKeys(keysToEnter);
        if (element.getAttribute("value").isEmpty()) {
            element.sendKeys(keysToEnter);
        }
    }

    public void scrollToElement(WebElement element) {
        js.executeScript("arguments[0].scrollIntoView(true);", element);
    }

    public void waitUntilAttributeContains(WebElement element, String attribute, String value) {
        try {
            wait.until(ExpectedConditions.attributeContains(element, attribute, value));
        } catch (WebDriverException e) {
            fail("Failed to wait until attribute '" + attribute + "' of element '" + element + "' contains value '" + value + "'");
        }
    }

    public void waitUntilInvisibilityOfElementLocated(WebElement element) {
        try {
            wait.until(ExpectedConditions.invisibilityOf(element));
        } catch (WebDriverException e) {
            fail("Element is visible");
        }
    }

    protected WebElement waitUntilVisibilityOfElementLocated(WebElement element) {
        try {
            return wait.until(ExpectedConditions.visibilityOf(element));
        } catch (WebDriverException e) {
            return fail("No visibility element: " + element.getTagName());
        }
    }

    public void goToNextTab(int tabNumber) {
        waitUntilNumberOfTabToBe(tabNumber);
        ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(tabs.get(tabNumber - 1));
    }

    public static String getRandomNumber() {
        StringBuilder random = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            random.append(ThreadLocalRandom.current().nextInt(0, 100));
        }
        return random.toString();
    }

    public static String randomUUID() {
        UUID randomUUID = UUID.randomUUID();
        return randomUUID.toString().replaceAll("_", "");
    }

    public static String random() {
        return getRandomNumber() + randomUUID().substring(0, 6);
    }

    public static char getRandomSymbol() {
        String s = "~`!@#$^&*()_+=-";
        return s.charAt(new Random().nextInt(s.length()));
    }

    public void captureScreen(WebDriver driver, String screenshotName) {
        if (driver instanceof TakesScreenshot) {
            Allure.addAttachment(screenshotName,
                    new ByteArrayInputStream(((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)));
        }
    }

    public void pull(WebElement element, int xOffset, int yOffset) {
        actions.clickAndHold(element).moveByOffset(xOffset, yOffset).release().perform();
    }

    public void waitUntilAttributeToBe(String locator, String attribute, String value) {
        try {
            wait.until(ExpectedConditions.attributeToBe(By.xpath(locator), attribute, value));
        } catch (WebDriverException e) {
            fail("Failed to wait until attribute '" + attribute + "' of element located by '" + locator + "' is '" + value + "'");
        }
    }

    public void clearInputField(WebElement element) {
        element.click();
        element.sendKeys(Keys.CONTROL + "A" + Keys.BACK_SPACE);
    }

    public void waitUntilAttributeToBeNotEmpty(WebElement element, String attribute) {
        try {
            wait.until(ExpectedConditions.attributeToBeNotEmpty(element, attribute));
        } catch (WebDriverException e) {
            fail("Failed to wait until attribute '" + attribute + "' of element '" + element + "' is not empty");
        }
    }
}
