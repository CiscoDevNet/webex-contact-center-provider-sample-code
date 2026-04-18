from voicevirtualagent_pb2 import VoiceVAResponse
from byova_common_pb2 import OutputEvent
from AudioUtils import AudioUtils
from EventUtils import EventUtils

class AudioProcessor:

    def __init__(self):
        self.audio_buffer = bytearray()
        self.start_of_input_sent = False
        self.is_barge_in_enabled = False

    def process_audio_event(self, audio_byte):
        try:
            if len(audio_byte) > 15: # The reason for greater than 15 bytes is, we (from webex) send some sample bytes (3 bytes) in the beginning of the call, and we want to ignore those bytes.
                # Check the received audio has any speech in it. If yes, proceed to the next steps. If not, you can ignore.
                self.audio_buffer.extend(audio_byte)
                if not self.start_of_input_sent:
                    print("Sending start_of_input")
                    yield EventUtils.get_va_response_for_output_event(EventUtils.get_output_event(OutputEvent.EventType.START_OF_INPUT))
                    self.start_of_input_sent = True
                if self._is_end_of_speech():
                    # Sending End of Input as we detected the user has finished speaking.
                    print("Sending end_of_input")
                    yield EventUtils.get_va_response_for_output_event(EventUtils.get_output_event(OutputEvent.EventType.END_OF_INPUT))
                    # Send the audio to AI service for processing and get the response back
                    yield from self._response_to_user_as_chunk()
        except Exception as ex:
            print(ex)


    def _response_to_user_as_chunk(self):
        self.audio_buffer.clear()
        ai_audio = AudioUtils.get_default_audio()        
        chunk_size = 640  # 80ms at 8kHz mu-law (1 byte/sample). Chunk size can be any value. Here it is set to make ~80ms of audio.
        for i in range(0, len(ai_audio), chunk_size):
            audio_chunk = ai_audio[i:i + chunk_size]
            yield EventUtils.get_audio_output_events_bytes(audio_chunk, "Transcript of the chunk", self.is_barge_in_enabled, VoiceVAResponse.ResponseType.CHUNK)
        # Once all chunks are sent, send RESPONSE_FINAL chunk without audio.
        yield EventUtils.get_audio_output_events_bytes(None, None, self.is_barge_in_enabled, VoiceVAResponse.ResponseType.FINAL)

    def _is_end_of_speech(self):
        # Here we need to process the audio and detect the end of speech. For simplicity, we are checking the buffer size.
        #print(f"Length of buffer {len(self.audio_buffer)}")
        return len(self.audio_buffer) > 800159
