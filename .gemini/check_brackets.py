import sys

def check_brackets(filename):
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
    
    stack = []
    mapping = {')': '(', '}': '{', ']': '['}
    
    for i, char in enumerate(content):
        line_num = content.count('\n', 0, i) + 1
        if char in mapping.values():
            stack.append((char, line_num))
        elif char in mapping.keys():
            if not stack:
                print(f"Stray '{char}' at line {line_num}")
                return
            top, start_line = stack.pop()
            if top != mapping[char]:
                print(f"Mismatched bracket at line {line_num}: found '{char}' but expected matching for '{top}' from line {start_line}")
                return
    
    if stack:
        for char, line in stack:
            print(f"Unclosed '{char}' starting at line {line}")
    else:
        print("All brackets are balanced.")

if __name__ == "__main__":
    check_brackets(sys.argv[1])
