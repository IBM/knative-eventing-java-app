import os, time, sys, datetime
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.keys import Keys

# Do an action on the app's landing page
options = Options()
options.add_argument('--headless')
options.add_argument('--no-sandbox')
options.add_argument('--disable-dev-shm-usage')
driver = webdriver.Chrome(options=options)
driver.get(os.environ["APP_URL"] + "/v1/events"); # Open a browser to the app's landing page
time.sleep(3)
# Verify the expected content is present
html = driver.page_source
print("The page content is: {}".format(html))
if title_text == "No events found in the database!":
    print("Experience Test Successful")
else:
    sys.exit("Experience Test Failed")

# TODO start the cron event source and check for events in the events page
