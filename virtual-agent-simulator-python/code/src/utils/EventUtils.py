from voicevirtualagent_pb2 import VoiceVAResponse, Prompt,VoiceVAInputMode
from byova_common_pb2 import OutputEvent, TextContent, InputHandlingConfig, DTMFInputConfig, DTMFDigits, InputSpeechTimers

class EventUtils:

    @staticmethod
    def get_audio_output_events_bytes(audio_bytes, transcript, barge_in_enabled, response_type):
        va_response = VoiceVAResponse()
        va_response.prompts.append(EventUtils.create_prompt_from_bytes(transcript, audio_bytes, barge_in_enabled))
        va_response.response_type = response_type
        va_response.input_mode = VoiceVAInputMode.INPUT_VOICE_DTMF
        va_response.input_handling_config.CopyFrom(InputHandlingConfig (
            dtmf_config = DTMFInputConfig(
                dtmf_input_length = 1,
                inter_digit_timeout_msec = 300,
                termchar = DTMFDigits.DTMF_DIGIT_POUND
            ),
            speech_timers = InputSpeechTimers (
                complete_timeout_msec = 5000,
                no_input_timeout_msec = 10000
            )
        ))
        return va_response


    @staticmethod     
    def get_output_event(event_type):
        output_event = OutputEvent()
        output_event.event_type = event_type
        return output_event
    
    @staticmethod
    def get_va_response_for_output_event(output_event):
        va_response = VoiceVAResponse()
        va_response.output_events.append(output_event)
        return va_response
    
    @staticmethod
    def create_prompt_from_bytes(text=None, audio_bytes=None, barge_in_enabled=False):
        prompt = Prompt()
        if text:
            prompt.text = text        
        if audio_bytes:    
            prompt.audio_content = audio_bytes        
        prompt.is_barge_in_enabled = barge_in_enabled
        return prompt

    @staticmethod
    def create_text_content(text):
        text_content = TextContent()
        text_content.text = text
        text_content.language_code = "en-US"
        return text_content
    