"""
Pydantic schemas for SMS Ingestion.
"""

from typing import Optional
from pydantic import BaseModel, Field
from schemas.transaction import TransactionResponse


class SMSIngestionRequest(BaseModel):
    """
    Schema for incoming raw SMS transaction ingestion.
    """

    raw_sms: str = Field(..., description="The raw content of the SMS.")
    sender: str = Field(..., description="The sender ID of the SMS (e.g. AD-HDFCBK).")


class SMSIngestionResponse(BaseModel):
    """
    Schema representing the ingestion result response.
    """

    success: bool = Field(..., description="Indicates if the SMS was successfully parsed and saved.")
    transaction: Optional[TransactionResponse] = Field(None, description="The created transaction details if successful.")
    message: str = Field(..., description="Feedback message regarding the ingestion status.")
