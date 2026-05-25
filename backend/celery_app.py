"""
Celery Configuration for Background Tasks.

Sets up the Celery instance, configures the Redis broker/backend,
and registers background tasks.
"""

import os
from celery import Celery
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")

# Initialize Celery app
celery = Celery(
    "expense_tracker",
    broker=REDIS_URL,
    backend=REDIS_URL,
)

# Optional configuration settings
celery.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
)


@celery.task(name="tasks.process_transaction_sms")
def process_transaction_sms(user_id: int, sender: str, body: str) -> dict:
    """
    Background task to process incoming bank/financial SMS and parse details
    using a rule engine or natural language processing model.

    Args:
        user_id (int): The ID of the user who received the SMS.
        sender (str): The SMS sender identifier (e.g., 'HDFC-Bank').
        body (str): The raw text of the SMS message.

    Returns:
        dict: The parsed transaction detail results.
    """
    print(f"Starting SMS parsing background job for user_id={user_id}...")
    
    # Simple mock heuristic parsing engine
    # In a full solution, this would use a transformer model or regular expressions.
    amount = 0.0
    tx_type = "debit"
    merchant = "Unknown Merchant"

    body_lower = body.lower()
    if "spent" in body_lower or "debited" in body_lower or "withdrawn" in body_lower:
        tx_type = "debit"
    elif "credited" in body_lower or "received" in body_lower:
        tx_type = "credit"

    # Quick amount parser mock
    # Parses strings like "Rs. 500" or "INR 500.00"
    for word in body.split():
        cleaned_word = "".join(c for c in word if c.isdigit() or c == ".")
        if cleaned_word and "." in cleaned_word:
            try:
                amount = float(cleaned_word)
                break
            except ValueError:
                continue

    print(f"Success: Parsed SMS body into amount={amount}, type={tx_type}")
    
    return {
        "user_id": user_id,
        "amount": amount,
        "type": tx_type,
        "merchant": merchant,
        "status": "parsed"
    }


@celery.task(name="tasks.send_budget_alert_notification")
def send_budget_alert_notification(user_id: int, category: str, percentage: float) -> str:
    """
    Background task to dispatch a push notification or email to a user
    when they exceed their budget threshold.

    Args:
        user_id (int): ID of the user.
        category (str): Category that has breached budget.
        percentage (float): Percentage of the budget utilized.

    Returns:
        str: Dispatched message confirmation.
    """
    print(f"Triggering budget alert notification for user_id={user_id}...")
    message = f"Alert! You have spent {percentage:.1f}% of your '{category}' monthly budget limit."
    
    # Email/Push mock delivery
    print(f"Message sent successfully: '{message}'")
    return f"Notification sent to user {user_id}."
